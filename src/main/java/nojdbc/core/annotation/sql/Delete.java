package nojdbc.core.annotation.sql;

import nojdbc.core.enumeration.ReturnType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для выполнения команд по удалению записей из таблиц.<br>
 *
 * Поддерживает возвращение данных удаленной строки, с помощью ключевого слова RETURNING,
 * так же можно возвращать списки, если удаляемых строк несколько, и определенные колонки.<br>
 * Возвращаемый тип должен соответствовать значениям {@link ReturnType}.
 */
@Documented
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface Delete {

    String value();

}
