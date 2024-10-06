package nojdbc.core.util;

import com.squareup.javapoet.TypeName;
import nojdbc.core.enumeration.ReturnType;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class TypeMirrorUtil {

    private static final List<String> SUPPORTED_TYPE_NAMES = List.of(
          String.class.getCanonicalName(),
          UUID.class.getCanonicalName(),
          OffsetDateTime.class.getCanonicalName(),
          OffsetTime.class.getCanonicalName(),
          LocalTime.class.getCanonicalName()
    );

    private final Types typeUtils;
    private final Elements elementUtils;
    private final List<TypeMirror> supportedTypes;

    public boolean isTypeMirrorSameAsClass(TypeMirror typeMirror, Class<?> clazz) {
        return typeUtils.isSameType(typeMirror, getTypeMirrorForClass(clazz));
    }

    public TypeMirrorUtil(Types typeUtils, Elements elementUtils) {
        this.typeUtils = typeUtils;
        this.elementUtils = elementUtils;
        supportedTypes = SUPPORTED_TYPE_NAMES.stream()
              .map(type -> elementUtils.getTypeElement(type).asType())
              .toList();
    }

    /**
     * Получение общего типа объекта принадлежащего к {@link DeclaredType}, <br>
     * иначе возвращает тип исходного объекта.
     *
     * @param typeMirror тип исходного объекта.
     * @return тип объекта.
     */
    public TypeMirror getGenericParamIfPossible(TypeMirror typeMirror) {
        if (typeMirror instanceof DeclaredType declaredType) {
            return declaredType.getTypeArguments()
                  .stream()
                  .map(e -> (TypeMirror) e)
                  .findFirst()
                  .orElse(typeMirror);
        }
        return typeMirror;
    }

    /**
     * Проверка возможности заполнения значения по входному типу.
     *
     * @param fieldType входной тип.
     * @return true - если тип элемента примитив или его обертка, или входит в список {@link  #SUPPORTED_TYPE_NAMES}, <br>
     *         иначе false.
     */
    public boolean canFillRow(TypeMirror fieldType) {
        final var fieldReturnType = getReturnType(fieldType);
        if (ReturnType.PRIMITIVE.equals(fieldReturnType) || ReturnType.BOXED_PRIMITIVE.equals(fieldReturnType)) {
            return true;
        }

        final var declaredElement = typeUtils.asElement(fieldType);
        return declaredElement.getKind() == ElementKind.ENUM || supportedTypes.contains(declaredElement.asType());
    }

    /**
     * Получение типа возвращенного объекта по типу входного объекта.
     *
     * @param returnTypeMirror тип входного объекта.
     * @return тип возвращаемого объекта.
     */
    public ReturnType getReturnType(TypeMirror returnTypeMirror) {
        if (returnTypeMirror == null || TypeKind.VOID.equals(returnTypeMirror.getKind())) {
            return ReturnType.VOID;
        }

        if (returnTypeMirror.getKind().isPrimitive()) {
            return ReturnType.PRIMITIVE;
        }
        if (TypeName.get(returnTypeMirror).isBoxedPrimitive()) {
            return ReturnType.BOXED_PRIMITIVE;
        }

        final var genericType = getGenericParamIfPossible(returnTypeMirror);
        if (typeUtils.isSameType(returnTypeMirror, getOptionalTypeFor(genericType))) {
            return ReturnType.OPTIONAL;
        }
        if (typeUtils.isSameType(returnTypeMirror, getSetTypeFor(genericType))) {
            return ReturnType.SET;
        }
        if (typeUtils.isSameType(returnTypeMirror, getListTypeFor(genericType))
              || typeUtils.isSameType(returnTypeMirror, getCollectionTypeFor(genericType))) {
            return ReturnType.LIST;
        }

        return ReturnType.VOID;
    }

    /**
     * Признак принадлежности типа входного объекта к коллекции.
     *
     * @param type тип входного объекта.
     * @return true - если тип входного объекта принадлежит коллекции, иначе false.
     */
    public boolean isCollection(TypeMirror type) {
        final var returnType = getReturnType(type);
        return ReturnType.LIST.equals(returnType)
              || ReturnType.SET.equals(returnType);
    }

    public TypeMirror getListType() {
        return getTypeMirrorForClass(List.class);
    }

    public TypeMirror getVoidType() {
        return getTypeMirrorForClass(Void.class);
    }

    public TypeElement getTypeElement(TypeMirror mirror) {
        return elementUtils.getTypeElement(mirror.toString());
    }

    public TypeMirror asTypeMirror(String type) {
        return elementUtils.getTypeElement(type).asType();
    }

    public Element asElement(TypeMirror type) {
        return typeUtils.asElement(type);
    }

    /**
     * Получение имени пакета типа элемента, если возможно, <br>
     * иначе возвращает значение по умолчанию.
     *
     * @param type         тип элемента.
     * @param defaultValue значение по умолчанию.
     * @return имя пакета.
     */
    public String getPackage(TypeElement type, String defaultValue) {
        final var packageName = type.asType()
              .toString()
              .replaceAll("\\." + type.getSimpleName(), "");

        return packageName.isBlank() ? defaultValue : packageName;
    }

    private <T> TypeMirror getTypeMirrorForClass(Class<T> clazz, TypeMirror... genericTypes) {
        return typeUtils.getDeclaredType(
              elementUtils.getTypeElement(clazz.getCanonicalName()),
              genericTypes
        );
    }

    private TypeMirror getOptionalTypeFor(TypeMirror genericType) {
        if (genericType.getKind().isPrimitive()) {
            genericType = typeUtils.boxedClass((PrimitiveType) genericType).asType();
        }
        return getTypeMirrorForClass(Optional.class, genericType);
    }

    private TypeMirror getSetTypeFor(TypeMirror genericType) {
        if (genericType.getKind().isPrimitive()) {
            genericType = typeUtils.boxedClass((PrimitiveType) genericType).asType();
        }
        return getTypeMirrorForClass(Set.class, genericType);
    }

    private TypeMirror getListTypeFor(TypeMirror genericType) {
        if (genericType.getKind().isPrimitive()) {
            genericType = typeUtils.boxedClass((PrimitiveType) genericType).asType();
        }
        return getTypeMirrorForClass(List.class, genericType);
    }

    private TypeMirror getCollectionTypeFor(TypeMirror genericType) {
        if (genericType.getKind().isPrimitive()) {
            genericType = typeUtils.boxedClass((PrimitiveType) genericType).asType();
        }
        return getTypeMirrorForClass(Collection.class, genericType);
    }

}
