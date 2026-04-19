package indi.etern.checkIn.utils;

import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.service.dao.SettingService;

import java.util.List;
import java.util.Map;

public class SaveSettingCommon {
    private final String rootName;
    final Map<String, Object> dataMap;
    final List<String> enabledKeys;
    
    public SaveSettingCommon(Map<String, Object> dataMap, String[] enabledKeys, String rootName) {
        this.dataMap = dataMap;
        this.enabledKeys = List.of(enabledKeys);
        this.rootName = rootName;
    }
    
    public void doSave() {
        List<SettingItem> settingItems = dataMap.entrySet().stream()
                .filter(entry -> {
                    String key = entry.getKey();
                    if (enabledKeys.contains(key)) {
                        return true;
                    }
                    if (key.startsWith(rootName + ".")) {
                        String keyWithoutPrefix = key.substring(rootName.length() + 1);
                        return enabledKeys.contains(keyWithoutPrefix);
                    }
                    return false;
                })
                .map(entry -> {
                    final Object value = entry.getValue();
                    String key = entry.getKey();
                    String finalKey;
                    if (key.startsWith(rootName + ".")) {
                        finalKey = key;
                    } else {
                        finalKey = rootName + "." + key;
                    }
                    Class<?> clazz = value != null ? value.getClass() : null;
                    return new SettingItem(finalKey, value, clazz);
                })
                .toList();
        SettingService.singletonInstance.setAll(settingItems);
    }
}
