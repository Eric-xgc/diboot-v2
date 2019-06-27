package com.diboot.core.binding.manager;

import com.diboot.core.binding.BaseBinder;
import com.diboot.core.binding.FieldBinder;
import com.diboot.core.binding.annotation.BindEntity;
import com.diboot.core.binding.annotation.BindEntityList;
import com.diboot.core.binding.annotation.BindField;
import com.diboot.core.binding.annotation.BindDict;
import com.diboot.core.binding.parser.BindAnnotationGroup;
import com.diboot.core.binding.parser.ConditionManager;
import com.diboot.core.binding.parser.FieldAnnotation;
import com.diboot.core.entity.Dictionary;
import com.diboot.core.service.BaseService;
import com.diboot.core.service.DictionaryService;
import com.diboot.core.util.BeanUtils;
import com.diboot.core.util.ContextHelper;
import com.diboot.core.util.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 绑定管理器
 * @author Mazhicheng
 * @version v2.0
 * @date 2019/3/30
 */
public class AnnotationBindingManager {
    private static final Logger log = LoggerFactory.getLogger(AnnotationBindingManager.class);

    /**
     * 自动转换和绑定VO中的注解关联
     * @param entityList
     * @param voClass
     * @param <E>
     * @param <VO>
     * @return
     */
    public static <E, VO> List<VO> autoConvertAndBind(List<E> entityList, Class<VO> voClass){
        // 转换为VO列表
        List<VO> voList = BeanUtils.convertList(entityList, voClass);
        // 自动绑定关联对象
        autoBind(voList);
        return voList;
    }

    /**
     * 自动绑定关联对象
     * @return
     * @throws Exception
     */
    public static <VO> void autoBind(List<VO> voList){
        if(V.isEmpty(voList)){
            return;
        }
        // 获取VO类
        Class voClass = voList.get(0).getClass();
        BindAnnotationGroup bindAnnotationGroup = BindAnnotationCacheManager.getBindAnnotationGroup(voClass);
        if(bindAnnotationGroup.isNotEmpty()){
            // 绑定数据字典
            List<FieldAnnotation> dictAnnoList = bindAnnotationGroup.getBindDictAnnotations();
            if(dictAnnoList != null){
                for(FieldAnnotation annotation : dictAnnoList){
                    doBindingDict(voList, annotation);
                }
            }
            // 绑定Field字段名
            List<FieldAnnotation> fieldAnnoList = bindAnnotationGroup.getBindFieldAnnotations();
            if(fieldAnnoList != null){
                doBindingField(voList, fieldAnnoList);
            }
            // 绑定Entity实体
            List<FieldAnnotation> entityAnnoList = bindAnnotationGroup.getBindEntityAnnotations();
            if(entityAnnoList != null){
                for(FieldAnnotation anno : entityAnnoList){
                    doBindingEntity(voList, anno);
                }
            }
            // 绑定Entity实体List
            List<FieldAnnotation> entitiesAnnoList = bindAnnotationGroup.getBindEntityListAnnotations();
            if(entitiesAnnoList != null){
                for(FieldAnnotation anno : entitiesAnnoList){
                    doBindingEntityList(voList, anno);
                }
            }
        }
    }

    /***
     * 绑定数据字典
     * @param voList
     * @param fieldAnno
     * @param <VO>
     */
    private static <VO> void doBindingDict(List<VO> voList, FieldAnnotation fieldAnno) {
        DictionaryService dictionaryService = (DictionaryService) ContextHelper.getBean(DictionaryService.class);
        if(dictionaryService != null){
            BindDict annotation = (BindDict) fieldAnno.getAnnotation();
            dictionaryService.bindItemLabel(voList, fieldAnno.getFieldName(), annotation.field(), annotation.type());
        }
    }

    /***
     * 绑定字段
     * @param voList
     * @param fieldAnnoList
     * @param <VO>
     */
    private static <VO> void doBindingField(List<VO> voList, List<FieldAnnotation> fieldAnnoList) {
        //多个字段，合并查询，以减少SQL数
        Map<String, List<FieldAnnotation>> clazzToListMap = new HashMap<>();
        for(FieldAnnotation anno : fieldAnnoList){
            BindField bindField = (BindField) anno.getAnnotation();
            String key = bindField.entity().getName() + ":" + bindField.condition();
            List<FieldAnnotation> list = clazzToListMap.computeIfAbsent(key, k -> new ArrayList<>());
            list.add(anno);
        }
        // 解析条件并且执行绑定
        for(Map.Entry<String, List<FieldAnnotation>> entry : clazzToListMap.entrySet()){
            List<FieldAnnotation> list = entry.getValue();
            BindField bindAnnotation = (BindField) list.get(0).getAnnotation();
            BaseService service = getService(bindAnnotation);
            FieldBinder binder = service.bindingFieldTo(voList);
            for(FieldAnnotation anno : list){
                BindField bindField = (BindField) anno.getAnnotation();
                binder.link(bindField.field(), anno.getFieldName());
            }
            parseConditionsAndBinding(binder, bindAnnotation.condition());
        }
    }

    /***
     * 绑定Entity
     * @param voList
     * @param fieldAnnotation
     * @param <VO>
     */
    private static <VO> void doBindingEntity(List<VO> voList, FieldAnnotation fieldAnnotation) {
        BindEntity annotation = (BindEntity) fieldAnnotation.getAnnotation();
        // 绑定关联对象entity
        BaseService service = getService(annotation);
        if(service != null){
            // 字段名
            String voFieldName = fieldAnnotation.getFieldName();
            // 构建binder
            BaseBinder binder = service.bindingEntityTo(voList).set(voFieldName);
            // 解析条件并且执行绑定
            parseConditionsAndBinding(binder, annotation.condition());
        }
    }

    /***
     * 绑定Entity
     * @param voList
     * @param fieldAnnotation
     * @param <VO>
     */
    private static <VO> void doBindingEntityList(List<VO> voList, FieldAnnotation fieldAnnotation) {
        BindEntityList bindAnnotation = (BindEntityList) fieldAnnotation.getAnnotation();
        // 绑定关联对象entity
        BaseService service = getService(bindAnnotation);
        if(service != null){
            // 字段名
            String voFieldName = fieldAnnotation.getFieldName();
            // 构建binder
            BaseBinder binder = service.bindingEntityListTo(voList).set(voFieldName);
            // 解析条件并且执行绑定
            parseConditionsAndBinding(binder, bindAnnotation.condition());
        }
    }

    /***
     * 解析条件并且执行绑定
     * @param condition
     * @param binder
     */
    private static void parseConditionsAndBinding(BaseBinder binder, String condition){
        try{
            ConditionManager.parseConditions(condition, binder);
            binder.bind();
        }
        catch (Exception e){
            log.error("解析注解条件与绑定执行异常", e);
        }
    }

    /**
     * 通过Entity获取对应的Service实现类
     * @param annotation
     * @return
     */
    private static BaseService getService(Annotation annotation){
        Class<?> entityClass = null;
        if(annotation instanceof BindDict){
            entityClass = Dictionary.class;
        }
        else if(annotation instanceof BindField){
            BindField bindAnnotation = (BindField)annotation;
            entityClass = bindAnnotation.entity();
        }
        else if(annotation instanceof BindEntity){
            BindEntity bindAnnotation = (BindEntity)annotation;
            entityClass = bindAnnotation.entity();
        }
        else if(annotation instanceof BindEntityList){
            BindEntityList bindAnnotation = (BindEntityList)annotation;
            entityClass = bindAnnotation.entity();
        }
        else{
            log.warn("非预期的注解: "+ annotation.getClass().getSimpleName());
            return null;
        }
        // 根据entity获取Service
        BaseService service = ContextHelper.getServiceByEntity(entityClass);
        if(service == null){
            log.error("未能识别到Entity: "+entityClass.getName()+" 的Service实现！");
        }
        return service;
    }

}
