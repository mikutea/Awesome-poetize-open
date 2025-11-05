package com.ld.poetry.utils;

import com.baomidou.mybatisplus.core.exceptions.MybatisPlusException;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Scanner;

/**
 * MyBatis-Plus代码生成器工具
 * 用于根据数据库表自动生成Entity、Mapper、Service、Controller等代码
 * 
 * 注意：本类是命令行工具，需要与用户进行交互，因此使用PrintStream进行输出
 */
@Slf4j
public class CodeGenerator {

    // 命令行输出流（用于工具类的用户交互）
    private static final PrintStream console = System.out;

    /**
     * 命令行交互：读取用户输入
     * 
     * @param tip 提示信息
     * @return 用户输入的内容
     */
    public static String scanner(String tip) {
        try (Scanner scanner = new Scanner(System.in)) {
            StringBuilder help = new StringBuilder();
            help.append("请输入" + tip + "：");
            // 代码生成器是命令行工具，需要与用户交互
            console.println(help.toString());
            if (scanner.hasNext()) {
                String ipt = scanner.next();
                if (StringUtils.isNotBlank(ipt)) {
                    log.info("用户输入 {}: {}", tip, ipt);
                    return ipt;
                }
            }
            throw new MybatisPlusException("请输入正确的" + tip + "！");
        }
    }

    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");
        
        FastAutoGenerator.create("jdbc:mysql://ip:port/poetize?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai", 
                                "username", "password")
                // 全局配置
                .globalConfig(builder -> {
                    builder.author("sara") // 设置作者
                            .disableOpenDir() // 禁止打开输出目录
                            .outputDir(projectPath + "\\src\\main\\java") // 指定输出目录
                            .dateType(DateType.TIME_PACK); // 时间策略
                })
                // 包配置
                .packageConfig(builder -> {
                    builder.parent("com.ld.poetry") // 设置父包名
                            .moduleName("") // 设置父包模块名
                            .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + "\\src\\main\\resources\\mapper")); // 设置mapperXml生成路径
                })
                // 策略配置
                .strategyConfig(builder -> {
                    builder.addInclude(scanner("表名，多个英文逗号分割").split(",")) // 设置需要生成的表名
                            .addTablePrefix("t_", "c_") // 设置过滤表前缀
                            // Entity 策略配置
                            .entityBuilder()
                            .enableLombok() // 开启 lombok 模式
                            .enableTableFieldAnnotation() // 开启生成实体时生成字段注解
                            .naming(NamingStrategy.underline_to_camel) // 数据库表映射到实体的命名策略
                            .columnNaming(NamingStrategy.underline_to_camel) // 数据库表字段映射到实体的命名策略
                            .logicDeleteColumnName("deleted") // 逻辑删除字段名(数据库)
                            // Mapper 策略配置
                            .mapperBuilder()
                            .superClass("com.baomidou.mybatisplus.core.mapper.BaseMapper") // 设置父类
                            .formatMapperFileName("%sMapper") // 格式化 mapper 文件名称
                            .formatXmlFileName("%sMapper") // 格式化 xml 实现类文件名称
                            // Service 策略配置
                            .serviceBuilder()
                            .formatServiceFileName("%sService") // 格式化 service 接口文件名称
                            .formatServiceImplFileName("%sServiceImpl") // 格式化 service 实现类文件名称
                            // Controller 策略配置
                            .controllerBuilder()
                            .enableHyphenStyle() // 开启驼峰转连字符
                            .enableRestStyle(); // 开启生成@RestController 控制器
                })
                .templateEngine(new VelocityTemplateEngine()) // 使用Velocity引擎模板，默认的是Velocity引擎模板
                .execute();
    }
}
