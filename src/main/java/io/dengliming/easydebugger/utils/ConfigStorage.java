package io.dengliming.easydebugger.utils;

import io.dengliming.easydebugger.constant.ConnectType;
import io.dengliming.easydebugger.model.ConnectConfig;
import io.dengliming.easydebugger.model.ConnectConfigWrapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 连接配置统一存储
 */
@Slf4j
public enum ConfigStorage {
    INSTANCE;

    private final String CONNECT_CONFIG_FILE_PATH = System.getProperty("user.home") + "/Documents/EasyDebugger/";
    private final String CONNECT_CONFIG_FILE = "connect-config.xml";
    private final String EMPTY_CONFIG_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n<connectConfigs></connectConfigs>";
    private final Map<String, ConnectConfig> connectConfigMap = new LinkedHashMap<>();

    ConfigStorage() {
        File file = getConnectConfigFile();
        if (file != null && file.exists()) {
            loadConnectConfigDataFromFile(file);
        }
    }

    public void loadConnectConfigDataFromFile(File file) {
        try {
            JAXBContext context = JAXBContext
                    .newInstance(ConnectConfigWrapper.class);
            Unmarshaller um = context.createUnmarshaller();
            ConnectConfigWrapper wrapper = (ConnectConfigWrapper) um.unmarshal(file);
            connectConfigMap.clear();
            if (wrapper.getConnectConfigs() != null) {
                wrapper.getConnectConfigs().forEach(it -> connectConfigMap.put(it.getUid(), it));
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
            wrapper.setConnectConfigs(getConnectConfigs());
            m.marshal(wrapper, file);
        } catch (Throwable e) {
            log.error("saveConnectConfigDataToFile error.", e);
        }
    }

    public File getConnectConfigFile() {
        try {
            File dir = new File(CONNECT_CONFIG_FILE_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(CONNECT_CONFIG_FILE_PATH + CONNECT_CONFIG_FILE);
            if (!file.exists()) {
                file.createNewFile(); // 如果文件不存在，则自动创建
                FileUtils.writeStringToFile(file, EMPTY_CONFIG_XML, StandardCharsets.UTF_8);
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
        connectConfigMap.put(config.getUid(), config);
        flushDb();
    }

    public void set(ConnectConfig config) {
        connectConfigMap.replace(config.getUid(), config);
        flushDb();
    }

    public void removeAll(Collection<ConnectConfig> config) {
        config.forEach(c -> connectConfigMap.remove(c.getUid()));
        flushDb();
    }

    public void removeAll(List<String> config) {
        config.forEach(c -> connectConfigMap.remove(c));
        flushDb();
    }

    public List<ConnectConfig> getConnectConfigs() {
        return connectConfigMap.values().stream().collect(Collectors.toList());
    }

    public List<ConnectConfig> getConnectConfigs(ConnectType connectType) {
        return connectConfigMap.values()
                .stream()
                .filter(it -> it.getConnectType() == connectType)
                .collect(Collectors.toList());
    }

    public ConnectConfig getConnectConfig(String id) {
        return id == null ? null : connectConfigMap.get(id);
    }
}
