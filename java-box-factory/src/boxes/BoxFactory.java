package boxes;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.WeakHashMap;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * A class which allows for the creation of 'box' objects. These are lightweight
 * wrappers around an existing object, supporting a given interface and allowing
 * the user to make calls upon the object without using reflection. For example:
 * 
 * <pre>
 * // Create an interface that supports getText() and setText(String).
 * public static interface TextObject {
 * 	public String getText();
 * 	public void setText(String text);
 * }
 * 
 * // Create a JLabel, and then wrap it in a TextObject interface.
 * JLabel jLabel = new JLabel(&quot;Hello&quot;);
 * BoxFactory boxFactory = new BoxFactory(false);
 * TextObject textObject = boxFactory.createBox(TextObject.class, jLabel);
 * 
 * // Access the JLabel object via the TextObject interface. 
 * System.out.println(textObject.getText());
 * textObject.setText(&quot;World&quot;);
 * System.out.println(jLabel.getText());
 * </pre>
 */
public class BoxFactory extends ClassLoader {
	private int classCount = 0;

	/**
	 * Map from 'real' class to 'box' interface to 'box' constructor.
	 */
	private final WeakHashMap<Class<?>, HashMap<Class<?>, Constructor<?>>> map = new WeakHashMap<Class<?>, HashMap<Class<?>, Constructor<?>>>();

	/**
	 * If the <code>throwExceptions</code> flag is true, then any missing
	 * methods will throw an instance of {@link BoxException}. If the
	 * <code>throwExceptions</code> flag is false, then any missing methods will
	 * return null or zero as appropriate.
	 */
	private final boolean throwExceptions;

	/**
	 * Create an instance of the 'box factory'.
	 * <p>
	 * If the <code>throwExceptions</code> flag is true, then any missing
	 * methods will throw an instance of {@link BoxException}. If the
	 * <code>throwExceptions</code> flag is false, then any missing methods will
	 * return null or zero as appropriate.
	 */
	public BoxFactory(boolean throwExceptions) {
		this.throwExceptions = throwExceptions;
	}

	/**
	 * Create a 'box' around an existing object, allowing you to call methods on
	 * that object without knowing the data type and without using reflection.
	 * <p>
	 * The given <code>boxInterface</code> must be a Java interface containing
	 * methods. The <code>realObject</code> should have methods with matching
	 * signatures (but this is not strictly required). The returned object will
	 * be an implementation of the <code>boxInterface</code> that allows you to
	 * call methods on <code>realObject</code> without resorting to reflection.
	 * <p>
	 * <b>Note:</b> If a method is missing from the <code>realObject</code>,
	 * then calling that method on the 'box' will either throw a
	 * {@link BoxException}, return null, or return 0 as appropriate. See
	 * {@link #BoxFactory(boolean)} for more information.
	 * 
	 * @param boxInterface
	 *            an interface to implement
	 * @param realObject
	 *            the real object to 'box'
	 * @return an implementation of the interface allowing you to call methods
	 *         on the real object
	 */
	public <T> T createBox(Class<T> boxInterface, Object realObject) {
		Class<?> realClass = realObject == null ? null : realObject.getClass();

		HashMap<Class<?>, Constructor<?>> map2 = map.get(realClass);
		if (map2 == null) {
			map2 = new HashMap<Class<?>, Constructor<?>>();
			map.put(realClass, map2);
		}

		Constructor<?> boxConstructor = map2.get(boxInterface);
		if (boxConstructor == null) {
			boxConstructor = createBoxConstructor(realClass, boxInterface);
			map2.put(boxInterface, boxConstructor);
		}

		try {
			return boxInterface.cast(boxConstructor.newInstance(realObject));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private Constructor<?> createBoxConstructor(Class<?> realClass, Class<?> boxClass) {
		HashMap<String, Method> realMethods = new HashMap<String, Method>();
		for (Method m : realClass.getMethods())
			realMethods.put(m.getName() + ":" + Type.getMethodDescriptor(m), m);

		String className = "Box" + classCount;
		classCount++;

		String boxInternalName = Type.getInternalName(boxClass);
		String realDescriptor = Type.getDescriptor(realClass);
		String realInternalName = Type.getInternalName(realClass);

		ClassWriter boxWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
		boxWriter.visit(Opcodes.V1_1, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", new String[] { boxInternalName });

		/* Create a field called 'real'. */
		FieldVisitor fieldWriter = boxWriter.visitField(Opcodes.ACC_PUBLIC, "real", realDescriptor, null, null);
		fieldWriter.visitEnd();

		String constructorDescriptor = "(" + realDescriptor + ")V";
		MethodVisitor constructorWriter = boxWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", constructorDescriptor, null, null);
		constructorWriter.visitVarInsn(Opcodes.ALOAD, 0);
		constructorWriter.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V");
		constructorWriter.visitVarInsn(Opcodes.ALOAD, 0);
		constructorWriter.visitVarInsn(Opcodes.ALOAD, 1);
		constructorWriter.visitFieldInsn(Opcodes.PUTFIELD, className, "real", realDescriptor);
		constructorWriter.visitInsn(Opcodes.RETURN);
		constructorWriter.visitMaxs(0, 0);
		constructorWriter.visitEnd();

		for (Method boxMethod : boxClass.getMethods()) {
			String methodDescriptor = Type.getMethodDescriptor(boxMethod);

			/* Get the parameters, exceptions, and return type. */
			Class<?>[] exceptionTypes = boxMethod.getExceptionTypes();
			Class<?>[] parameterTypes = boxMethod.getParameterTypes();
			Class<?> returnType = boxMethod.getReturnType();

			String[] exceptionInternalNames = new String[exceptionTypes.length];
			for (int i = 0; i < exceptionTypes.length; i++)
				exceptionInternalNames[i] = Type.getInternalName(exceptionTypes[i]);

			MethodVisitor methodWriter = boxWriter.visitMethod(Opcodes.ACC_PUBLIC, boxMethod.getName(), methodDescriptor, null,
					exceptionInternalNames);
			methodWriter.visitVarInsn(Opcodes.ALOAD, 0);
			methodWriter.visitFieldInsn(Opcodes.GETFIELD, className, "real", realDescriptor);

			Method realMethod = realMethods.get(boxMethod.getName() + ":" + methodDescriptor);
			if (realMethod == null) {
				if (throwExceptions) {
					methodWriter.visitTypeInsn(Opcodes.NEW, "com/actenum/util/BoxException");
					methodWriter.visitInsn(Opcodes.DUP);
					methodWriter.visitMethodInsn(Opcodes.INVOKESPECIAL, "com/actenum/util/BoxException", "<init>", "()V");
					methodWriter.visitInsn(Opcodes.ATHROW);
				} else if (returnType == Void.TYPE)
					methodWriter.visitInsn(Opcodes.RETURN);
				else if (!returnType.isPrimitive()) {
					methodWriter.visitInsn(Opcodes.ACONST_NULL);
					methodWriter.visitInsn(Opcodes.ARETURN);
				} else if (returnType == Double.TYPE) {
					methodWriter.visitInsn(Opcodes.DCONST_0);
					methodWriter.visitInsn(Opcodes.DRETURN);
				} else if (returnType == Float.TYPE) {
					methodWriter.visitInsn(Opcodes.FCONST_0);
					methodWriter.visitInsn(Opcodes.FRETURN);
				} else if (returnType == Long.TYPE) {
					methodWriter.visitInsn(Opcodes.LCONST_0);
					methodWriter.visitInsn(Opcodes.LRETURN);
				} else {
					methodWriter.visitInsn(Opcodes.ICONST_0);
					methodWriter.visitInsn(Opcodes.IRETURN);
				}
			} else {
				for (int i = 0; i < parameterTypes.length; i++)
					methodWriter.visitVarInsn(Opcodes.ALOAD, i + 1);
				methodWriter.visitMethodInsn(Opcodes.INVOKEVIRTUAL, realInternalName, realMethod.getName(), methodDescriptor);

				if (returnType == Void.TYPE)
					methodWriter.visitInsn(Opcodes.RETURN);
				else if (!returnType.isPrimitive())
					methodWriter.visitInsn(Opcodes.ARETURN);
				else if (returnType == Double.TYPE)
					methodWriter.visitInsn(Opcodes.DRETURN);
				else if (returnType == Float.TYPE)
					methodWriter.visitInsn(Opcodes.FRETURN);
				else if (returnType == Long.TYPE)
					methodWriter.visitInsn(Opcodes.LRETURN);
				else
					methodWriter.visitInsn(Opcodes.IRETURN);
			}

			methodWriter.visitMaxs(0, 0);
			methodWriter.visitEnd();
		}

		boxWriter.visitEnd();

		byte[] byteArray = boxWriter.toByteArray();
		Class<?> result = this.defineClass(className, byteArray, 0, byteArray.length);
		try {
			return result.getConstructor(realClass);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		return null;
	}
}
