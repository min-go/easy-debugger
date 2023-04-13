package io.dengliming.easydebugger.view;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.util.ResourceBundle;

@Slf4j
public class MainController implements Initializable {

    @FXML
    private TabPane tabPane;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // 添加两个Tab，每个Tab对应一个Controller
        addTab("tcp-client-view", new TcpClientController());
        addTab("tcp-server-view", new TcpServerController());
        addTab("udp-client-view", new UdpClientController());
    }

    private void addTab(String title, Object controller) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + title.toLowerCase() + ".fxml"));
            loader.setController(controller);

            Tab tab = loader.load();
            tabPane.getTabs().add(tab);
        } catch (Exception e) {
            log.error("addTab({}) error.", title, e);
        }
    }
}
