package com.aiolos.badger.user.provider.nacos;

import com.aiolos.common.util.UrlUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shardingsphere.driver.jdbc.core.driver.ShardingSphereDriverURLProvider;

import java.util.Map;
import java.util.Properties;

public class NacosDriverURLProvider implements ShardingSphereDriverURLProvider {
    
    private static final String NACOS_TYPE = "nacos:";
    
    @Override
    public boolean accept(String url) {
        return StringUtils.isNotBlank(url) && url.contains(NACOS_TYPE);
    }

    /**
     * @param url jdbc:shardingsphere:nacos:127.0.0.1:8848:badger-user-provider-sharding-jdbc.yaml?username=nacos&password=nacos&namespace=dev&group=DEFAULT_GROUP
     * @return
     */
    @Override
    public byte[] getContent(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }

        String substring = url.substring(url.lastIndexOf(NACOS_TYPE) + NACOS_TYPE.length());
        String[] split = substring.split("\\?");
        String jdbcUrl = split[0];
        String[] jdbcUrlArr = jdbcUrl.split(":");
        String serverAddr = jdbcUrlArr[0] + ":" + jdbcUrlArr[1];
        String dataId = jdbcUrlArr[2];

        String nacosConfig = split[1];
        Map<String, String> paramMap = UrlUtil.urlParse(nacosConfig);
        String username = paramMap.get("username");
        String password = paramMap.get("password");
        String namespace = paramMap.get("namespace");
        String group = paramMap.get("group");

        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, serverAddr);
        properties.setProperty(PropertyKeyConst.USERNAME, username);
        properties.setProperty(PropertyKeyConst.PASSWORD, password);
        properties.setProperty(PropertyKeyConst.NAMESPACE, namespace);
        try {
            ConfigService configService = NacosFactory.createConfigService(properties);
            String config = configService.getConfig(dataId, group, 5000);
            return config.getBytes();
        } catch (NacosException e) {
            throw new RuntimeException(e);
        }
    }
}
