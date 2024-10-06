package nojdbc.core.pojo;

import com.squareup.javapoet.ParameterSpec;
import nojdbc.core.enumeration.ParamType;

import javax.lang.model.type.TypeMirror;

public record RequestParam(
      ParameterSpec spec,
      TypeMirror genericTypeMirror,
      ParamType type,
      String modification
) {

    public RequestParam(
          ParameterSpec spec,
          TypeMirror genericTypeMirror,
          ParamType type
    ) {
        this(spec, genericTypeMirror, type, "");
    }

    public RequestParam(
          RequestParam param,
          String modification
    ) {
        this(param.spec, param.genericTypeMirror, param.type, modification);
    }

}
