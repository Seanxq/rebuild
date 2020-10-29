/*
Copyright (c) REBUILD <https://getrebuild.com/> and/or its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.core.configuration.general;

import cn.devezhao.persist4j.Entity;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.rebuild.core.Application;
import com.rebuild.core.configuration.ConfigBean;
import com.rebuild.core.configuration.ConfigManager;
import com.rebuild.core.configuration.ConfigurationException;
import com.rebuild.core.metadata.MetadataHelper;
import com.rebuild.core.metadata.impl.EasyMeta;
import com.rebuild.utils.JSONUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录转换映射
 *
 * @author devezhao
 * @since 2020/10/27
 */
public class TransformManager implements ConfigManager {

    public static final TransformManager instance = new TransformManager();

    private TransformManager() { }

    /**
     * 前端使用
     *
     * @param sourceEntity
     * @return
     */
    public JSONArray getTransforms(String sourceEntity, ID user) {
        JSONArray data = new JSONArray();
        for (ConfigBean c : getRawTransforms(sourceEntity)) {
            // 尚未配置
            if (c.getJSON("config") == null) continue;

            String target = c.getString("target");
            Entity targetEntity = MetadataHelper.getEntity(target);
            if (!Application.getPrivilegesManager().allowCreate(user, targetEntity.getEntityCode())) {
                continue;
            }

            EasyMeta easyMeta = EasyMeta.valueOf(targetEntity);
            JSONObject item = JSONUtils.toJSONObject(
                    new String[] { "entityIcon", "entityLabel", "transid" },
                    new Object[] { easyMeta.getIcon(), easyMeta.getLabel(), c.getID("id") });
            data.add(item);
        }
        return data;
    }

    /**
     * @param configId
     * @param sourceEntity
     * @return
     */
    public ConfigBean getTransformConfig(ID configId, String sourceEntity) {
        if (sourceEntity == null) {
            sourceEntity = getBelongEntity(configId);
        }

        for (ConfigBean c : getRawTransforms(sourceEntity)) {
            if (configId.equals(c.getID("id"))) {
                return c.clone();
            }
        }

        throw new ConfigurationException("No `TransformConfig` found : " + configId);
    }

    /**
     * @return
     */
    @SuppressWarnings("unchecked")
    protected List<ConfigBean> getRawTransforms(String sourceEntity) {
        final String cKey = "TransformManager-" + sourceEntity;
        Object cached = Application.getCommonsCache().getx(cKey);
        if (cached != null) {
            return (List<ConfigBean>) cached;
        }

        Object[][] array = Application.createQueryNoFilter(
                "select belongEntity,targetEntity,configId,config from TransformConfig where belongEntity = ? and isDisabled = 'F'")
                .setParameter(1, sourceEntity)
                .array();

        ArrayList<ConfigBean> entries = new ArrayList<>();
        for (Object[] o : array) {
            ConfigBean entry = new ConfigBean()
                    .set("source", o[0])
                    .set("target", o[1])
                    .set("id", o[2]);

            JSON config = JSON.parseObject((String) o[3]);
            entry.set("config", config);

            entries.add(entry);
        }

        Application.getCommonsCache().putx(cKey, entries);
        return entries;
    }

    private String getBelongEntity(ID configId) {
        Object[] o = Application.createQueryNoFilter(
                "select belongEntity from TransformConfig where configId = ?")
                .setParameter(1, configId)
                .unique();

        if (o == null) {
            throw new ConfigurationException("No `TransformConfig` found : " + configId);
        }
        return (String) o[0];
    }

    @Override
    public void clean(Object cfgid) {
        String cKey = "TransformManager-" + getBelongEntity((ID) cfgid);
        Application.getCommonsCache().evict(cKey);
    }
}
