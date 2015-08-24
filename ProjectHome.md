A simple class that takes a Java interface and a Java object, and builds a 'box' or wrapper that implements the interface and passes calls directly through to the Java object. It uses ASM to generate bytecode dynamically, avoiding Java reflection overhead. The result is 1.6x to 20x faster than using Java reflection directly, depending on how much you abuse the Java reflection framework.

## Simple example ##

```
 // Create an interface that supports getText() and setText(String).
 public static interface TextObject {
      public String getText();
      public void setText(String text);
 }
 
 // Create a JLabel, and then box it in a TextObject interface.
 JLabel jLabel = new JLabel("Hello");
 BoxFactory boxFactory = new BoxFactory(false);
 TextObject textObject = boxFactory.createBox(TextObject.class, jLabel);
 
 // Access the JLabel object via the new TextObject instance. 
 System.out.println(textObject.getText());
 textObject.setText("World");

 // Or via the regular JLabel object.
 System.out.println(jLabel.getText());
```

## Realistic example ##

```
 // Create an interface that supports getText() and setText(String).
 public static interface TextObject {
      public String getText();
      public void setText(String text);
 }
 
 // Get an unknown object from somewhere, and box it in a TextObject interface.
 Object unknownObject = ...;
 BoxFactory boxFactory = new BoxFactory(false);
 TextObject textObject = boxFactory.createBox(TextObject.class, unknownObject);
 
 // Access the unknown object via the new TextObject instance. 
 System.out.println(textObject.getText());
 textObject.setText("World");
```