package boxes;

import java.lang.reflect.Method;

import javax.swing.JLabel;

public class BoxTest {
	private static final Class<?>[] NO_TYPES = new Class<?>[0];

	private static final Class<?>[] STRING_TYPE = new Class<?>[] { String.class };

	private static final Object[] NO_ARGUMENTS = new Object[0];

	private static final Object[] ONE_ARGUMENT = new Object[1];

	public static interface TextObject {
		public String getText();

		public void setText(String text);
	}

	public static void main(String[] args) throws Exception {

		/*
		 * Create an object of 'unknown' type, but which we believe has a
		 * 'getText()' method on it.
		 */
		Object unknownObject = new JLabel("Trevor");

		/*
		 * Create a 'box factory' which can be used to wrap objects and provide
		 * access to their methods.
		 */
		BoxFactory boxFactory = new BoxFactory(true);

		System.out.println("CREATE AND CALL BOX vs FETCH AND INVOKE METHOD");
		System.out.println("----------------------------------------------");
		for (int j = 0; j < 10; j++) {
			long time = System.currentTimeMillis();
			for (int i = 1; i <= 1000000; i++) {
				TextObject textObject = boxFactory.createBox(TextObject.class, unknownObject);
				String text = textObject.getText();
				textObject.setText(text);
			}
			System.out.println("BoxFactory: " + (System.currentTimeMillis() - time));

			time = System.currentTimeMillis();
			for (int i = 1; i <= 1000000; i++) {
				Method get = unknownObject.getClass().getMethod("getText", NO_TYPES);
				Method set = unknownObject.getClass().getMethod("setText", STRING_TYPE);
				String text = (String) get.invoke(unknownObject, NO_ARGUMENTS);
				ONE_ARGUMENT[0] = text;
				set.invoke(unknownObject, ONE_ARGUMENT);
			}
			System.out.println("Reflection: " + (System.currentTimeMillis() - time));
		}

		System.out.println();
		System.out.println("JUST CALL BOX vs JUST INVOKE METHOD");
		System.out.println("----------------------------------------------");
		for (int j = 0; j < 10; j++) {
			TextObject textObject = boxFactory.createBox(TextObject.class, unknownObject);
			long time = System.currentTimeMillis();
			for (int i = 1; i <= 100000000; i++) {
				String text = textObject.getText();
				textObject.setText(text);
			}
			System.out.println("BoxFactory: " + (System.currentTimeMillis() - time));

			Method get = unknownObject.getClass().getMethod("getText", NO_TYPES);
			Method set = unknownObject.getClass().getMethod("setText", STRING_TYPE);
			time = System.currentTimeMillis();
			for (int i = 1; i <= 100000000; i++) {
				String text = (String) get.invoke(unknownObject, NO_ARGUMENTS);
				ONE_ARGUMENT[0] = text;
				set.invoke(unknownObject, ONE_ARGUMENT);
			}
			System.out.println("Reflection: " + (System.currentTimeMillis() - time));
		}
	}
}
