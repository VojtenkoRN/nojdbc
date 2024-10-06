package nojdbc.core.pojo;

import nojdbc.core.enumeration.ReturnType;

import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ReturnParam(
      TypeMirror type,
      ReturnType returnType,
      TypeMirror genericParam,
      ReturnType genericParamReturnType
) {

    public Class<?> getGenericType() {
        return switch (returnType) {
            case OPTIONAL -> Optional.class;
            case SET -> Set.class;
            case LIST -> List.class;
            default -> Void.class;
        };
    }

}
