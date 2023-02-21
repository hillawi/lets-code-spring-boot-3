package letscode.boot3;


import lombok.extern.slf4j.Slf4j;
import org.postgresql.Driver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.Assert;

import javax.sql.DataSource;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

@Slf4j
public class Boot3Application {

    public static void main(String[] args) {
        var applicationContext = new AnnotationConfigApplicationContext();
        applicationContext.register(DataConfiguration.class);
        applicationContext.refresh();
        applicationContext.start();

        var cs = applicationContext.getBean(CustomerService.class);
        log.info("cs.class={}", cs.getClass().getName());
        var juergen = cs.add("Jürgen");
        var stephane = cs.add("Stéphane");
        var josh = cs.add("Josh");

        var all = cs.all();
        Assert.state(all.contains(juergen) && all.contains(stephane), "we didn't add Stéphane and Jürgen successfully!");
        all.forEach(c -> log.info(c.toString()));
    }
}

@Slf4j
@Configuration
@EnableTransactionManagement
@ComponentScan
class DataConfiguration {

    @Bean
    static MyBeanDefinitionRegistryPostProcessor myBeanDefinitionRegistryPostProcessor() {
        return new MyBeanDefinitionRegistryPostProcessor();
    }

    static class MyBeanDefinitionRegistryPostProcessor implements BeanDefinitionRegistryPostProcessor {

        @Override
        public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
            var jdbcTemplateBeanDefinition = BeanDefinitionBuilder
                    .genericBeanDefinition(CustomerService.class)
                    .addConstructorArgReference("jdbcTemplate")
                    .getBeanDefinition();
            registry.registerBeanDefinition("cs", jdbcTemplateBeanDefinition);
        }

        @Override
        public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

        }
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager ptm) {
        return new TransactionTemplate(ptm);
    }

    @Bean
    DataSourceTransactionManager dataSourceTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean
    DriverManagerDataSource dataSource(Environment environment) {
        var dataSource = new DriverManagerDataSource("jdbc:postgresql://localhost/postgres", "postgres", "postgres");
        dataSource.setDriverClassName(Driver.class.getName());
        return dataSource;
    }
}

@Slf4j
class CustomerService {

    private final JdbcTemplate template;
    private final RowMapper<Customer> customerRowMapper = (resultSet, rowNum) -> new Customer(resultSet.getInt("id"), resultSet.getString("name"));

    CustomerService(JdbcTemplate template) {
        this.template = template;
    }

    public Customer add(String name) {
        var al = new ArrayList<Map<String, Object>>();
        al.add(Map.of("id", Long.class));
        var keyHolder = new GeneratedKeyHolder(al);
        template.update(con -> {
            var ps = con.prepareStatement("""
                    insert into customers (name) values(?)
                    on conflict on constraint customers_name_key do update set name = excluded.name  
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            return ps;
        }, keyHolder);
        var generatedId = keyHolder.getKeys().get("id");
        Assert.state(generatedId instanceof Number, "the generatedId must be a Number!");
        var number = (Number) generatedId;
        return byId(number.intValue());
    }

    public Customer byId(Integer id) {
        return template.queryForObject("select id, name  from customers where id =? ", customerRowMapper, id);
    }

    public Collection<Customer> all() {
        return template.query("select id, name  from customers", this.customerRowMapper);
    }


}

record Customer(Integer id, String name) {
}

