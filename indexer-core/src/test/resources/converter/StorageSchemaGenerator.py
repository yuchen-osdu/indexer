import json
import sys

class StorageSchemaGenerator(object):
    BASIC_TYPES = ['number', 'string', 'integer', 'boolean']
    DEFINITIONS = 'definitions'
    PROPERTIES = 'properties'
    SPECIALS = ['AbstractFeatureCollection.1.0.0', 'AbstractAnyCrsFeatureCollection.1.0.0',
                'geoJsonFeatureCollection', 'core_dl_geopoint']  # do not expand these
    SKIP = ['AbstractAnyCrsFeatureCollection.1.0.0']  # this is irrelevant to the indexer
    DE_TYPES = {'AbstractFeatureCollection.1.0.0': 'core:dl:geoshape:1.0.0',
                'geoJsonFeatureCollection': 'core:dl:geoshape:1.0.0',
                'core_dl_geopoint': 'core:dl:geopoint:1.0.0'}  # this ones is understood

    def __init__(self, schema: dict, schema_id: str):
        self.all_properties = list()
        self.sub_schema_stack = list()
        self.__where_we_have_been = list()
        self.__schema = schema
        self.__schema_id = schema_id
        self.__definitions = dict()
        self.__make_definitions_dictionary()
        self.__scan_schema()

    def __make_definitions_dictionary(self):
        if isinstance(self.__schema, dict) and self.DEFINITIONS in self.__schema:
            for key, definition in self.__schema[self.DEFINITIONS].items():
                self.__definitions[key] = definition

    def __scan_schema(self):
        schema = self.__schema.get(self.PROPERTIES)
        if schema is not None:
            self.__aggregate_dictionary_or_string('', schema)

    def de_schema(self) -> dict:
        schema = list()
        for prp in self.all_properties:
            if 'kind' in prp and 'path' in prp and \
                    (prp['key'].startswith('.data.') or prp['key'].startswith('.Data.')) \
                    and prp['kind'] != 'object':
                schema.append({'kind': prp['kind'], 'path': prp['key'][6:]})
        return {'kind': self.__schema_id, 'schema': schema}

    def property_list(self):
        p_l = list()
        last_sub_schema = ''
        for prp in self.all_properties:
            typ = prp['type']
            if 'subSchema' in prp:
                ss = prp['subSchema']
            else:
                ss = ''
            if ss != last_sub_schema and ss != '':
                k = self.__strip_last_property(prp['key'])
                l_i = '\t'.join([k, ss, ''])
                if l_i not in p_l:
                    p_l.append(l_i)
                last_sub_schema = ss
            k = self.__keep_last_property(prp['key'])
            if ss == '':
                ss = [k]
            else:
                ss = [ss, k]
            ss = '|'.join(ss)
            p_l.append('\t'.join([prp['key'], typ, ss]))
        return p_l

    @staticmethod
    def __strip_last_property(key):
        parts = key.split('.')
        stripped = '.'.join(parts[0:len(parts) - 1])
        return stripped

    @staticmethod
    def __keep_last_property(key):
        parts = key.split('.')
        stripped = parts[len(parts) - 1]
        return stripped

    def __get_definition_by_ref(self, reference):
        ref = reference.replace('#/definitions/', '')
        if ref in self.__definitions:
            return self.__definitions[ref]
        else:
            return None

    @staticmethod
    def __is_base_type_array_item(schema_fragment):
        if isinstance(schema_fragment, dict) and \
                'type' in schema_fragment and 'items' in schema_fragment and \
                schema_fragment['type'] == 'array' and 'type' in schema_fragment['items'] and \
                schema_fragment['items']['type'] in StorageSchemaGenerator.BASIC_TYPES:
            return True
        return False

    @staticmethod
    def __is_array_array_item(schema_fragment) -> bool:
        if isinstance(schema_fragment, dict) and \
                'type' in schema_fragment and 'items' in schema_fragment and \
                schema_fragment['type'] == 'array' and 'type' in schema_fragment['items'] and \
                'array' in schema_fragment['items']['type']:
            return True
        return False

    @staticmethod
    def __is_object_type_array_item(schema_fragment) -> bool:
        if isinstance(schema_fragment, dict) and \
                'type' in schema_fragment and 'items' in schema_fragment and \
                schema_fragment['type'] == 'array' and 'type' in schema_fragment['items'] and \
                schema_fragment['items']['type'] == 'object':
            return isinstance(schema_fragment['items']['properties'], dict)
        return False

    @staticmethod
    def __get_value_type_format(schema_fragment):
        v_t = ''
        if isinstance(schema_fragment, dict):
            fmt = ''
            if 'type' in schema_fragment:
                v_t = schema_fragment['type']
                if v_t == 'number':
                    v_t = 'double'
            if 'format' in schema_fragment:
                fmt = schema_fragment['format']
            if fmt == 'int64' and (v_t == 'integer' or v_t == 'number' or v_t == 'double'):
                v_t = 'long'
            elif (fmt.startswith('date') or fmt.startswith('time')) and v_t == 'string':
                v_t = 'datetime'
            if v_t == 'integer':
                v_t = 'int'
            elif v_t == 'boolean':
                v_t = 'bool'
        return v_t

    def __aggregate_dictionary_or_string(self, key: str, schema_fragment):
        if isinstance(schema_fragment, str) and schema_fragment == 'object':
            self.__make_de_schema(key.replace('.type', ''), schema_fragment)
        elif isinstance(schema_fragment, dict):
            self.__aggregate_schema_fragment(key, schema_fragment)
        else:
            pass  # this is title, description, pattern, or custom JSON tag, etc.

    def __aggregate_schema_fragment(self, key: str, schema_fragment):
        if 'allOf' in schema_fragment or 'oneOf' in schema_fragment or 'anyOf' in schema_fragment:
            self.__aggregate_all_any_one_of(key, schema_fragment)
        elif 'const' in schema_fragment:
            self.__make_de_schema(key, 'string')
        elif 'properties' in schema_fragment:
            for p_k, value in schema_fragment['properties'].items():
                self.__aggregate_dictionary_or_string(key + '.' + p_k, value)
        elif 'type' in schema_fragment and schema_fragment['type'] in self.BASIC_TYPES:
            v_type = self.__get_value_type_format(schema_fragment)
            pattern = schema_fragment.get('pattern', 'None')
            self.__make_de_schema(key, v_type, pattern)
        elif self.__is_base_type_array_item(schema_fragment):
            self.__aggregate_simple_array(key + '[]', schema_fragment)
        elif self.__is_array_array_item(schema_fragment):
            for p_k, value in schema_fragment.items():
                self.__aggregate_dictionary_or_string(key + '[]', value)
        elif self.__is_object_type_array_item(schema_fragment):
            self.__aggregate_array(key + '[]', schema_fragment)
        else:  # this should only be a dictionary
            self.__aggregate_dictionary(key, schema_fragment)

    def __aggregate_all_any_one_of(self, key: str, schema_fragment):
        if 'allOf' in schema_fragment:
            for part in schema_fragment['allOf']:
                self.__aggregate_dictionary_or_string(key, part)
        elif 'oneOf' in schema_fragment or 'anyOf' in schema_fragment:
            if 'oneOf' in schema_fragment:
                what = 'oneOf'
            else:
                what = 'anyOf'
            idx = min(len(schema_fragment[what]), 1)
            self.__aggregate_dictionary_or_string(key, schema_fragment[what][idx])

    def __aggregate_dictionary(self, key: str, schema_fragment: dict):
        for p_k, value in schema_fragment.items():
            if p_k == '$ref':
                if value not in self.__where_we_have_been:
                    self.__aggregate_d_ref(key, value)
            elif p_k == 'items':  # array
                if '$ref' in value:
                    v = value['$ref']
                    if v not in self.__where_we_have_been:
                        self.__aggregate_d_ref(key + '[]', v)
            else:
                self.__aggregate_dictionary_or_string(key + '.' + p_k, value)

    def __aggregate_array(self, key: str, schema_fragment):
        for p_k, value in schema_fragment['items']['properties'].items():
            v_type = self.__get_value_type_format(value)
            k = '{}[].{}'.format(key, p_k)
            if v_type == '':
                self.__aggregate_dictionary_or_string(key + '.' + p_k, value)
            else:
                self.__make_de_schema(k, v_type)

    def __aggregate_simple_array(self, key: str, schema_fragment):
        v_type = ''
        pattern = 'None'
        if 'type' in schema_fragment['items']:
            v_type = self.__get_value_type_format(schema_fragment['items'])
            pattern = schema_fragment['items'].get('pattern', 'None')
        self.__make_de_schema(key, v_type, pattern)

    @staticmethod
    def __get_sub_schema(value):
        parts = value.split('/')
        if len(parts) == 3:
            return parts[len(parts) - 1]
        else:
            return None

    def __aggregate_d_ref(self, key: str, value):
        self.__where_we_have_been.append(value)
        s_f = self.__get_definition_by_ref(value)
        ss = self.__get_sub_schema(value)
        if ss:
            self.sub_schema_stack.append(ss)
        if ss in self.SPECIALS:
            self.__make_de_schema(key, ss)
        else:
            self.__aggregate_dictionary_or_string(key, s_f)
        self.__where_we_have_been.pop()
        if ss:
            self.sub_schema_stack.pop()

    def __make_de_schema(self, key_string, v_type, pattern='None'):
        item = dict()
        item['key'] = key_string.replace('[]', '', -1)
        item['type'] = v_type
        if v_type == 'string' and pattern.startswith('^srn:'):
            kind = 'link'
        else:
            kind = self.DE_TYPES.get(v_type, v_type)
        k = key_string.replace('.Data.', '')
        if k.endswith('[]'):
            if k.count('[]') == 1:
                item['kind'] = '[]' + kind
                item['path'] = k.replace('[]', '')
            # else  ignore, nested arrays are not supported
        elif '[]' not in k:
            item['kind'] = kind
            item['path'] = k
        if len(self.sub_schema_stack) > 0:
            item['subSchema'] = self.sub_schema_stack[len(self.sub_schema_stack) - 1]
        if v_type not in self.SKIP:
            self.all_properties.append(item)


with open(sys.argv[1]) as json_file:
    schema = json.load(json_file)
    kind = 'osdu:osdu:Wellbore:1.0.0'
    generator = StorageSchemaGenerator(schema, kind)
    de_schema = generator.de_schema()

with open(sys.argv[1] + '.res', 'w') as fp:
    json.dump(de_schema, fp, indent = 2)
