module com.example.networks1project {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.xml;
    requires java.rmi;
    requires java.prefs;


    opens com.example.networks1project to javafx.fxml;
    exports com.example.networks1project;
    exports com.example.networks1project.controller;
    opens com.example.networks1project.controller to javafx.fxml;
}