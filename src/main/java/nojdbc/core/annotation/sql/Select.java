package nojdbc.core.annotation.sql;

import nojdbc.core.enumeration.ReturnType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для выполнения команд по выборке записей в таблице.<br>
 *
 * Возвращаемый тип должен соответствовать значениям {@link ReturnType}.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Select {

    String value();

    /**
     * Признак необходимости заполнения связанных полей (полей заполняемых с помощью других запросов).
     *
     * @return true - необходимо заполнять связанные поля, false - заполнять связанные поля не нужно.
     */
    boolean fillSubfields() default true;

    /**
     * <p> Признак необходимости использования кэширования для результата запроса.
     * <p> <b> Обязательно устанавливать для методов, используемых для извлечения связанных полей,
     * иначе их извлечение может привести к бесконечному циклу! </b>
     *
     * @return true - необходимо кэшировать результата запроса, false - кэшировать результат запроса не нужно.
     */
    boolean fieldFiller() default false;

}
