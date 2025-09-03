module org.lida {
	requires org.json;
    requires jdk.jsobject;
    requires com.fasterxml.jackson.databind;
	requires java.sql;

	requires javafx.controls;
    requires javafx.fxml;

	requires jdk.compiler;
	requires org.jgrapht.ext;
	requires javafx.swing;

	opens org.lida to javafx.fxml;
    exports org.lida;
    exports org.lida.Settings;
    opens org.lida.Settings to javafx.fxml;
    exports org.lida.Interface;
    opens org.lida.Interface to javafx.fxml;
    exports org.lida.Functionality;
    opens org.lida.Functionality to javafx.fxml;
	exports org.lida.Entity;
	opens org.lida.Entity to javafx.fxml;
}