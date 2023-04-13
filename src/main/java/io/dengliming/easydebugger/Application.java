package io.dengliming.easydebugger;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * 统一程序入口（主窗口）
 */
public class Application extends javafx.application.Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 设置窗口标题和大小
        primaryStage.setTitle("Easy Debugger");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);

        FXMLLoader loader = new FXMLLoader();
        loader.setLocation(getClass().getResource("/fxml/main.fxml"));
        TabPane tabPane = loader.load();

        /*BorderPane borderPane = new BorderPane();
        borderPane.setCenter(tabPane);
        HBox welcomeBox = new HBox();
        Label welcomeLabel = new Label("欢迎使用EasyDebugger～");
        welcomeBox.getChildren().add(welcomeLabel);
        welcomeBox.setAlignment(Pos.CENTER);
        welcomeBox.setPadding(new Insets(10));
        borderPane.setTop(welcomeBox);*/

        // 创建场景
        Scene scene = new Scene(tabPane);

        // 设置场景
        primaryStage.setScene(scene);
        primaryStage.show();
        primaryStage.setOnCloseRequest(event -> System.exit(0));
    }

    public static void main(String[] args) {
        launch();
    }
}