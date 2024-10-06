package nojdbc.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface SqlMapper {

    /**
     * Transactional status.
     *
     * @return If true use connection manager, otherwise create new connection for each request.
     */
    boolean transactional() default true;

}
