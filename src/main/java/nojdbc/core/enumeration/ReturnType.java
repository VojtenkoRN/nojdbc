package nojdbc.core.enumeration;

import java.util.List;

public enum ReturnType {

    VOID,
    PRIMITIVE,
    BOXED_PRIMITIVE,
    OPTIONAL,
    LIST,
    SET;

    private static final List<ReturnType> COLLECTION_TYPES = List.of(LIST, SET);
    private static final List<ReturnType> FIELD_FILLING_TYPES = List.of(OPTIONAL, LIST, SET);
    private static final List<ReturnType> PRIMITIVE_TYPES = List.of(PRIMITIVE, BOXED_PRIMITIVE);

    public boolean isCollection() {
        return COLLECTION_TYPES.contains(this);
    }

    public boolean isFieldFilling() {
        return FIELD_FILLING_TYPES.contains(this);
    }

    public boolean isPrimitiveType() {
        return PRIMITIVE_TYPES.contains(this);
    }

    public boolean isOptional() {
        return OPTIONAL.equals(this);
    }

}
