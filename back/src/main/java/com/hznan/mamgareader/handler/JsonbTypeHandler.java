package com.hznan.mamgareader.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;
import tools.jackson.databind.ObjectMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.OTHER)
//PostgreSQL 的 jsonb 是数据库特有类型，MyBatis 不自带转换器！,这个转换器可以实现java map 和 pg 的jsonb相互间的转换
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType) throws SQLException {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("JSON 序列化失败", e);
        }
        ps.setObject(i, pgObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parseJson(rs.getString(columnName));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parseJson(rs.getString(columnIndex));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parseJson(cs.getString(columnIndex));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) throws SQLException {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new SQLException("JSON 反序列化失败", e);
        }
    }
}
