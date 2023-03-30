package io.dengliming.easydebugger.model;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * 包装连接配置列表，用于保存列表到XML
 */
@XmlRootElement(name = "connectConfigs")
public class ConnectConfigWrapper {

    private List<ConnectConfig> connectConfigs;

    @XmlElement(name = "connectConfig")
    public List<ConnectConfig> getConnectConfigs() {
        return connectConfigs;
    }

    public void setConnectConfigs(List<ConnectConfig> connectConfigs) {
        this.connectConfigs = connectConfigs;
    }
}
