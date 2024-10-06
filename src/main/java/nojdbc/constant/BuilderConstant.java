package nojdbc.constant;

import nojdbc.core.enumeration.RequestType;

import java.util.List;

public final class BuilderConstant {

    public static final String DAO_NAME_SUFFIX = "Dao";
    public static final String CONNECTION_NAME = "connection";
    public static final String RECORD_NAME = "record";
    public static final String STATEMENT_NAME = "statement";
    public static final String RESULT_SET_NAME = "resultSet";
    public static final String CONNECTION_MANAGER_NAME = "connectionPool";
    public static final String DATASOURCE_NAME = "jdbc";
    public static final String IMPL_NAME_SUFFIX = "Impl";
    public static final String CACHE_NAME = "CACHE";
    public static final List<RequestType> REQUEST_TYPES_TO_BATCH = List.of(RequestType.INSERT, RequestType.UPDATE);
    public static final String TYPE_DB_VARCHAR = "varchar";
    public static final String TYPE_DB_TIMESTAMP_TZ = "timestamptz";
    public static final String TYPE_DB_TIME = "time";

    private BuilderConstant() {
    }

}
