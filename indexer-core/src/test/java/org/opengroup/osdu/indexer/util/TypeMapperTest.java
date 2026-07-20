/*
 * Copyright 2017-2025, The Open Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.opengroup.osdu.indexer.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.opengroup.osdu.core.common.Constants;
import org.opengroup.osdu.core.common.model.indexer.ElasticType;
import org.opengroup.osdu.core.common.model.indexer.Records;
import org.opengroup.osdu.core.common.model.indexer.StorageType;
import org.opengroup.osdu.core.common.model.search.RecordMetaAttribute;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TypeMapperTest {

    @Test
    public void validate_indexerMapping_schemaTypes() {
        assertEquals(ElasticType.KEYWORD.getValue(), TypeMapper.getIndexerType(StorageType.LINK.getValue(), ""));
        assertEquals(ElasticType.KEYWORD_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.LINK_ARRAY.getValue(), ""));
        assertEquals(ElasticType.BOOLEAN.getValue(), TypeMapper.getIndexerType(StorageType.BOOLEAN.getValue(), ""));
        assertEquals(ElasticType.BOOLEAN_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.BOOLEAN_ARRAY.getValue(), ""));
        assertEquals(ElasticType.TEXT.getValue(), TypeMapper.getIndexerType(StorageType.STRING.getValue(), ""));
        assertEquals(ElasticType.TEXT_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.STRING_ARRAY.getValue(), ""));
        assertEquals(ElasticType.INTEGER.getValue(), TypeMapper.getIndexerType(StorageType.INT.getValue(), ""));
        assertEquals(ElasticType.INTEGER_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.INT_ARRAY.getValue(), ""));
        assertEquals(ElasticType.FLOAT.getValue(), TypeMapper.getIndexerType(StorageType.FLOAT.getValue(), ""));
        assertEquals(ElasticType.FLOAT_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.FLOAT_ARRAY.getValue(), ""));
        assertEquals(ElasticType.DOUBLE.getValue(), TypeMapper.getIndexerType(StorageType.DOUBLE.getValue(), ""));
        assertEquals(ElasticType.DOUBLE_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.DOUBLE_ARRAY.getValue(), ""));
        assertEquals(ElasticType.LONG.getValue(), TypeMapper.getIndexerType(StorageType.LONG.getValue(), ""));
        assertEquals(ElasticType.LONG_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.LONG_ARRAY.getValue(), ""));
        assertEquals(ElasticType.DATE.getValue(), TypeMapper.getIndexerType(StorageType.DATETIME.getValue(), ""));
        assertEquals(ElasticType.DATE_ARRAY.getValue(), TypeMapper.getIndexerType(StorageType.DATETIME_ARRAY.getValue(), ""));
        assertEquals(ElasticType.GEO_POINT.getValue(), TypeMapper.getIndexerType(StorageType.GEO_POINT.getValue(), ""));
        assertEquals(ElasticType.GEO_SHAPE.getValue(), TypeMapper.getIndexerType(StorageType.GEO_SHAPE.getValue(), ""));
        assertEquals(ElasticType.OBJECT.getValue(), TypeMapper.getIndexerType("[]object", ""));
        assertEquals(ElasticType.NESTED.getValue(), TypeMapper.getIndexerType("nested", ""));
        assertEquals(ElasticType.FLATTENED.getValue(), TypeMapper.getIndexerType("flattened", ""));
    }

    @Test
    public void validate_meta_attributes() {
        List<String> keys = TypeMapper.getMetaAttributesKeys();

        String[] meta = new String[]{"id", "kind", "authority", "source", "namespace", "type", "version", "acl", "tags", "legal", "ancestry", "createUser", "modifyUser", "createTime", "modifyTime", "index"};
        for (String attributeKey : meta) {
            assertTrue(keys.contains(attributeKey));
        }
    }

    @Test
    public void validate_metaAttribute_indexerMapping() {
        // id, kind, namespace, type, createUser, modifyUser, x-acl
        String[] keywordMappedMetaAttribute = new String[]{"id", "kind", "namespace", "type", "createUser", "modifyUser", "x-acl"};
        for (String attributeKey : keywordMappedMetaAttribute) {
            Object idMappingValue = TypeMapper.getMetaAttributeIndexerMapping(attributeKey, "");
            Records.Type value = (Records.Type) idMappingValue;
            assertEquals("keyword", value.getType());
        }

        // authority, source
        Object authorityAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.AUTHORITY.getValue(), "osdu");
        Map<String, Object> authorityAttributeIndexMapping = (Map<String, Object>) authorityAttribute;
        assertEquals("constant_keyword", authorityAttributeIndexMapping.get("type"));
        assertEquals("osdu", authorityAttributeIndexMapping.get("value"));

        Object sourceAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.SOURCE.getValue(), "welldb");
        Map<String, Object> sourceAttributeIndexMapping = (Map<String, Object>) sourceAttribute;
        assertEquals("constant_keyword", sourceAttributeIndexMapping.get("type"));
        assertEquals("welldb", sourceAttributeIndexMapping.get("value"));

        // version
        Object versionAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.VERSION.getValue(), "");
        Records.Type versionAttributeIndexMapping = (Records.Type) versionAttribute;
        assertEquals("long", versionAttributeIndexMapping.getType());

        // acl
        Object aclAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.ACL.getValue(), "");
        Map<String, Object> aclAttributeIndexMapping = (Map<String, Object>) aclAttribute;
        Map<String, Object> aclProperties = (Map<String, Object>) aclAttributeIndexMapping.get("properties");
        Records.Type viewersMapping = (Records.Type) aclProperties.get("viewers");
        assertEquals("keyword", viewersMapping.getType());
        Records.Type ownersMapping = (Records.Type) aclProperties.get("owners");
        assertEquals("keyword", ownersMapping.getType());

        // tags
        Object tagsAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.TAGS.getValue(), "");
        Records.Type tagsAttributeIndexMapping = (Records.Type) tagsAttribute;
        assertEquals("flattened", tagsAttributeIndexMapping.getType());

        // legal
        Object legalAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.LEGAL.getValue(), "");
        Map<String, Object> legalAttributeIndexMapping = (Map<String, Object>) legalAttribute;
        Map<String, Object> legalProperties = (Map<String, Object>) legalAttributeIndexMapping.get("properties");
        Records.Type legaltagsMapping = (Records.Type) legalProperties.get("legaltags");
        assertEquals("keyword", legaltagsMapping.getType());
        Records.Type otherRelevantDataCountriesMapping = (Records.Type) legalProperties.get("otherRelevantDataCountries");
        assertEquals("keyword", otherRelevantDataCountriesMapping.getType());
        Records.Type statusMapping = (Records.Type) legalProperties.get("status");
        assertEquals("keyword", statusMapping.getType());

        // parents
        Object ancestryAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.ANCESTRY.getValue(), "");
        Map<String, Object> ancestryAttributeIndexMapping = (Map<String, Object>) ancestryAttribute;
        Map<String, Object> ancestryProperties = (Map<String, Object>) ancestryAttributeIndexMapping.get("properties");
        Records.Type parentsMapping = (Records.Type) ancestryProperties.get("parents");
        assertEquals("keyword", parentsMapping.getType());

        // index
        Object indexStatusAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.INDEX_STATUS.getValue(), "");
        Map<String, Object> indexStatusAttributeIndexMapping = (Map<String, Object>) indexStatusAttribute;
        Map<String, Object> indexStatusProperties = (Map<String, Object>) indexStatusAttributeIndexMapping.get("properties");
        Records.Type statusCodeMapping = (Records.Type) indexStatusProperties.get("statusCode");
        assertEquals("integer", statusCodeMapping.getType());
        Records.Type traceMapping = (Records.Type) indexStatusProperties.get("trace");
        assertEquals("text", traceMapping.getType());
        Records.Type tracelastUpdateTime = (Records.Type) indexStatusProperties.get("lastUpdateTime");
        assertEquals("date", tracelastUpdateTime.getType());

        // createTime
        Object createTimeAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.CREATE_TIME.getValue(), "");
        Records.Type createTimeAttributeIndexMapping = (Records.Type) createTimeAttribute;
        assertEquals("date", createTimeAttributeIndexMapping.getType());

        // modifyTime
        Object modifyTimeAttribute = TypeMapper.getMetaAttributeIndexerMapping(RecordMetaAttribute.MODIFY_TIME.getValue(), "");
        Records.Type modifyTimeAttributeIndexMapping = (Records.Type) modifyTimeAttribute;
        assertEquals("date", modifyTimeAttributeIndexMapping.getType());
    }

    @Test
    public void validate_dataAttribute_ofTextType_indexerMapping() {
        Object textMapping = TypeMapper.getDataAttributeIndexerMapping(ElasticType.TEXT.getValue(), false, false, false);
        Map<String, Object> textAttributeIndexMapping = (Map<String, Object>) textMapping;
        verifyTextIndexMapping_without_customAnalyzer_enabled(textAttributeIndexMapping);
    }

    @Test
    public void validate_dataAttribute_ofTextType_indexerMapping_with_customAnalyzer_enabled() {
        Object textMapping = TypeMapper.getDataAttributeIndexerMapping(ElasticType.TEXT.getValue(), false, false, true);
        Map<String, Object> textAttributeIndexMapping = (Map<String, Object>) textMapping;
        verifyTextIndexMapping_with_customAnalyzer_enabled(textAttributeIndexMapping);
    }

    @Test
    public void validate_dataAttribute_ofKeywordType_indexerMapping() {
        Object keywordMapping = TypeMapper.getDataAttributeIndexerMapping(ElasticType.KEYWORD.getValue(), false, false, false);
        Map<String, Object> keywordIndexMapping = (Map<String, Object>) keywordMapping;
        assertEquals("keyword", keywordIndexMapping.get("type"));
    }

    @Test
    public void validate_dataAttribute_ofNestedDataType_indexerMapping() {
        Map<String, Object> nestedAttribute = new HashMap<>();
        nestedAttribute.put("TerminationDateTime", "date");
        nestedAttribute.put("EffectiveDateTime", "date");
        nestedAttribute.put("FacilityStateTypeID", "text");
        Map<String, Object> facilityStateDataAttribute = new HashMap<>();
        facilityStateDataAttribute.put("type", "nested");
        facilityStateDataAttribute.put(Constants.PROPERTIES, nestedAttribute);

        Object nestedDataAttributeMapping = TypeMapper.getDataAttributeIndexerMapping(facilityStateDataAttribute, false, false, false);
        Map<String, Object> nestedDataAttributeIndexMapping = (Map<String, Object>) nestedDataAttributeMapping;
        assertEquals("nested", nestedDataAttributeIndexMapping.get("type"));
        Map<String, Object> propertiesIndexMapping = (Map<String, Object>) nestedDataAttributeIndexMapping.get("properties");
        Records.Type terminationDateTimeIndexType = (Records.Type) propertiesIndexMapping.get("TerminationDateTime");
        assertEquals("date", terminationDateTimeIndexType.getType());
        Records.Type effectiveDateTimeIndexType = (Records.Type) propertiesIndexMapping.get("EffectiveDateTime");
        assertEquals("date", effectiveDateTimeIndexType.getType());
        Map<String, Object> facilityStateIndexMapping = (Map<String, Object>) propertiesIndexMapping.get("FacilityStateTypeID");
        verifyTextIndexMapping_without_customAnalyzer_enabled(facilityStateIndexMapping);
    }

    @Test
    public void validate_dataAttribute_ofOtherType_indexerMapping() {
        List<ElasticType> types = Arrays.asList(
                ElasticType.CONSTANT_KEYWORD,
                ElasticType.DATE,
                ElasticType.OBJECT,
                ElasticType.FLATTENED,
                ElasticType.GEO_POINT,
                ElasticType.GEO_SHAPE,
                ElasticType.INTEGER,
                ElasticType.LONG,
                ElasticType.FLOAT,
                ElasticType.DOUBLE,
                ElasticType.BOOLEAN);
        for (ElasticType type : types) {
            Object indexerMapping = TypeMapper.getDataAttributeIndexerMapping(type.getValue(), false, false, false);
            Records.Type indexType = (Records.Type) indexerMapping;
            assertEquals(type.getValue(), indexType.getType());
        }
    }

    @Test
    public void validate_dataAttribute_ofArrayType_indexerMapping() {
        Object textArrayMapping = TypeMapper.getDataAttributeIndexerMapping(ElasticType.TEXT_ARRAY.getValue(), false, false, false);
        Map<String, Object> textArrayIndexMapping = (Map<String, Object>) textArrayMapping;
        verifyTextIndexMapping_without_customAnalyzer_enabled(textArrayIndexMapping);

        Object keywordArrayMapping = TypeMapper.getDataAttributeIndexerMapping(ElasticType.KEYWORD_ARRAY.getValue(), false, false, false);
        Map<String, Object> keywordArrayIndexMapping = (Map<String, Object>) keywordArrayMapping;
        assertEquals("keyword", keywordArrayIndexMapping.get("type"));

        List<ElasticType> types = Arrays.asList(ElasticType.INTEGER_ARRAY, ElasticType.LONG_ARRAY, ElasticType.FLOAT_ARRAY, ElasticType.DOUBLE_ARRAY, ElasticType.BOOLEAN_ARRAY, ElasticType.DATE_ARRAY);
        for (ElasticType type : types) {
            Object indexerMapping = TypeMapper.getDataAttributeIndexerMapping(type.getValue(), false, false, false);
            Records.Type indexType = (Records.Type) indexerMapping;
            assertEquals(StringUtils.substringBefore(type.getValue(), "_"), indexType.getType());
        }
    }

    @Test
    public void validate_dataAttribute_ofNestedAttribute_indexerMapping() {
        // text
        Map<String, Object> textAttribute = new HashMap<>();
        textAttribute.put("name", "text");
        Map<String, Object> nestedTextDataAttribute = new HashMap<>();
        nestedTextDataAttribute.put(Constants.PROPERTIES, textAttribute);
        Object textMapping = TypeMapper.getDataAttributeIndexerMapping(nestedTextDataAttribute, false, false, false);
        Map<String, Object> textPropertiesIndexMapping = (Map<String, Object>) ((Map<String, Object>) textMapping).get("properties");
        verifyTextIndexMapping_without_customAnalyzer_enabled((Map<String, Object>) textPropertiesIndexMapping.get("name"));

        // keyword
        Map<String, Object> keywordAttribute = new HashMap<>();
        keywordAttribute.put("name", "keyword");
        Map<String, Object> nestedKeywordDataAttribute = new HashMap<>();
        nestedKeywordDataAttribute.put(Constants.PROPERTIES, keywordAttribute);
        Object keywordMapping = TypeMapper.getDataAttributeIndexerMapping(nestedKeywordDataAttribute, false, false, false);
        Map<String, Object> keywordPropertiesIndexMapping = (Map<String, Object>) ((Map<String, Object>) keywordMapping).get("properties");
        Map<String, Object> keywordIndexMapping = (Map<String, Object>) keywordPropertiesIndexMapping.get("name");
        assertEquals("keyword", keywordIndexMapping.get("type"));

        // array types
        verifyNestedAttribute(ElasticType.TEXT_ARRAY.getValue(), "text");
        verifyNestedAttribute(ElasticType.KEYWORD_ARRAY.getValue(), "keyword");
        verifyNestedAttribute(ElasticType.INTEGER_ARRAY.getValue(), "integer");
        verifyNestedAttribute(ElasticType.LONG_ARRAY.getValue(), "long");
        verifyNestedAttribute(ElasticType.FLOAT_ARRAY.getValue(), "float");
        verifyNestedAttribute(ElasticType.DOUBLE_ARRAY.getValue(), "double");
        verifyNestedAttribute(ElasticType.BOOLEAN_ARRAY.getValue(), "boolean");
        verifyNestedAttribute(ElasticType.DATE_ARRAY.getValue(), "date");

        // object type
        Map<String, Object> objectAttribute = new HashMap<>();
        objectAttribute.put("HistoricalInterests", "object");
        Map<String, Object> nestedObjectDataAttribute = new HashMap<>();
        nestedObjectDataAttribute.put(Constants.PROPERTIES, objectAttribute);
        Object objectIndexMapping = TypeMapper.getDataAttributeIndexerMapping(nestedObjectDataAttribute, false, false, false);
        Map<String, Object> objectPropertiesIndexMapping = (Map<String, Object>) ((Map<String, Object>) objectIndexMapping).get("properties");
        Records.Type historicalInterestsIndexMapping = (Records.Type) objectPropertiesIndexMapping.get("HistoricalInterests");
        assertEquals("object", historicalInterestsIndexMapping.getType());

        // flattened
        Map<String, Object> flattenedAttribute = new HashMap<>();
        flattenedAttribute.put("FacilitySpecifications", "flattened");
        Map<String, Object> nestedFlattenedDataAttribute = new HashMap<>();
        nestedFlattenedDataAttribute.put(Constants.PROPERTIES, flattenedAttribute);
        Object flattenedIndexMapping = TypeMapper.getDataAttributeIndexerMapping(nestedFlattenedDataAttribute, false, false, false);
        Map<String, Object> flattenedPropertiesIndexMapping = (Map<String, Object>) ((Map<String, Object>) flattenedIndexMapping).get("properties");
        Records.Type facilitySpecificationsIndexMapping = (Records.Type) flattenedPropertiesIndexMapping.get("FacilitySpecifications");
        assertEquals("flattened", facilitySpecificationsIndexMapping.getType());
    }

    private static void verifyNestedAttribute(String arrayType, String memberType) {
        Map<String, Object> textArrayAttribute = new HashMap<>();
        textArrayAttribute.put("name", arrayType);
        Map<String, Object> nestedTextArrayDataAttribute = new HashMap<>();
        nestedTextArrayDataAttribute.put(Constants.PROPERTIES, textArrayAttribute);
        Object textArrayMapping = TypeMapper.getDataAttributeIndexerMapping(nestedTextArrayDataAttribute, false, false, false);
        Map<String, Object> textArrayPropertiesIndexMapping = (Map<String, Object>) ((Map<String, Object>) textArrayMapping).get("properties");
        Records.Type textArrayType = (Records.Type) textArrayPropertiesIndexMapping.get("name");
        assertEquals(memberType, textArrayType.getType());
    }

    private static void verifyTextIndexMapping_without_customAnalyzer_enabled(Map<String, Object> textAttributeIndexMapping) {
        verifyTextIndexMapping(textAttributeIndexMapping);
        assertNull(textAttributeIndexMapping.getOrDefault("analyzer", null));
    }

    private static void verifyTextIndexMapping_with_customAnalyzer_enabled(Map<String, Object> textAttributeIndexMapping) {
        verifyTextIndexMapping(textAttributeIndexMapping);
        assertEquals("osdu_custom_analyzer", textAttributeIndexMapping.getOrDefault("analyzer", null));
    }

    private static void verifyTextIndexMapping(Map<String, Object> textAttributeIndexMapping) {
        assertEquals("text", textAttributeIndexMapping.get("type"));
        Map<String, Object> fieldsMap = (Map<String, Object>) textAttributeIndexMapping.get("fields");
        Map<String, Object> keywordMap = (Map<String, Object>) fieldsMap.get("keyword");
        assertEquals("keyword", keywordMap.get("type"));
        assertEquals(256, keywordMap.get("ignore_above"));
        assertEquals("null", keywordMap.get("null_value"));
    }

}
