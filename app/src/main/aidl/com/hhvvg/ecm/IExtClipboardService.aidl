// IExtClipboardService.aidl
package com.hhvvg.ecm;

import com.hhvvg.ecm.configuration.AutoClearStrategyInfo;

interface IExtClipboardService {

    void setEnable(boolean enable);
    boolean isEnable();

    void setAutoClearEnable(boolean enable);
    boolean isAutoClearEnable();
    int getAutoClearWorkMode();
    void setAutoClearWorkMode(int mode);
    int getAutoClearReadCount();
    void setAutoClearReadCount(int count);
    void setAutoClearAppWhitelist(in List<String> exclusions);
    void setAutoClearAppBlacklist(in List<String> exclusions);
    List<String> getAutoClearAppBlacklist();
    List<String> getAutoClearAppWhitelist();
    void setAutoClearContentExclusionList(in List<String> exclusions);
    List<String> getAutoClearContentExclusionList();

    void setAutoClearTimeout(long timeout);
    long getAutoClearTimeout();

    List<AutoClearStrategyInfo> getAutoClearStrategies();
    void addAutoClearStrategy(in AutoClearStrategyInfo strategy);
    void removeStrategy(String packageName);

    void setAppReadWhitelist(in List<String> exclusions);
    void setAppWriteWhitelist(in List<String> exclusions);
    List<String> getAppReadWhitelist();
    List<String> getAppWriteWhitelist();
    void setReadWhiteEnable(boolean enable);
    boolean isReadWhiteEnable();
    // 同步剪切板
    boolean isSyncEnable();
    void setSyncEnable(boolean enable);

    boolean isSyncPullOnlyEnable();
    void setSyncPullOnlyEnable(boolean enable);

    boolean isSyncEncryptionEnable();
    void setSyncEncryptionEnable(boolean enable);

    String getSyncWsServer();
    void setSyncWsServer(String addr);

    String getSyncEncryptionKey();
    void setSyncEncryptionKey(String key);

    String getSyncEncryptionIV();
    void setSyncEncryptionIV(String iv);

    String getSyncAuthToken();
    void setSyncAuthToken(String authToken);
}
