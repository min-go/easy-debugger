package io.dengliming.easydebugger.utils;

import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.model.ConnectConfigWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Slf4j
public enum ConfigStorage {
    INSTANCE;

    private final List<ConnectConfig> connectConfigs = new ArrayList<>();
    private final String connectConfigFilePath = System.getProperty("user.home") + "/Documents/EasyDebugger/";
    private final String connectConfigFile = "connect-config.xml";

    public void loadConnectConfigDataFromFile(File file) {
        try {
            JAXBContext context = JAXBContext
                    .newInstance(ConnectConfigWrapper.class);
            Unmarshaller um = context.createUnmarshaller();
            ConnectConfigWrapper wrapper = (ConnectConfigWrapper) um.unmarshal(file);
            connectConfigs.clear();
            if (wrapper.getConnectConfigs() != null) {
                connectConfigs.addAll(wrapper.getConnectConfigs());
            }
        } catch (Exception e) {
            log.error("loadConnectConfigDataFromFile error.", e);
        }
    }

    public void saveConnectConfigDataToFile(File file) {
        try {
            JAXBContext context = JAXBContext
                    .newInstance(ConnectConfigWrapper.class);
            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            ConnectConfigWrapper wrapper = new ConnectConfigWrapper();
            wrapper.setConnectConfigs(connectConfigs);
            m.marshal(wrapper, file);
        } catch (Throwable e) {
            log.error("saveConnectConfigDataToFile error.", e);
        }
    }

    public File getConnectConfigFile() {
        try {
            File dir = new File(connectConfigFilePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(connectConfigFilePath + connectConfigFile);
            if (!file.exists()) {
                file.createNewFile(); // 如果文件不存在，则自动创建
                FileUtils.writeStringToFile(file, "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<connectConfigs></connectConfigs>", StandardCharsets.UTF_8);
            }
            return file;
        } catch (Exception e) {
            log.error("getConnectConfigFile error.", e);
        }
        return null;
    }

    public void flushDb() {
        saveConnectConfigDataToFile(getConnectConfigFile());
    }

    public void add(ConnectConfig config) {
        connectConfigs.add(config);
        flushDb();
    }

    public void removeAll(Collection<ConnectConfig> config) {
        connectConfigs.removeAll(config);
        flushDb();
    }

    public List<ConnectConfig> getConnectConfigs() {
        return connectConfigs;
    }
}
