package nojdbc.core.annotation.result;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Repeatable(Results.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface Result {

    /**
     * Поле в возвращаемом классе, что необходимо заполнять запросом.
     *
     * @return имя поля
     */
    String field() default "";

    /**
     * Поле таблицы, что будет передано в запрос для заполнения {@link #field}.
     *
     * @return имя поля
     */
    String column() default "";

    /**
     * Полный тип поля таблицы, что будет передано в запрос для заполнения {@link #field}.
     *
     * @return имя поля
     */
    String columnType() default "java.util.UUID";

    One one() default @One;

    Many many() default @Many;

    String converter() default "";

}
