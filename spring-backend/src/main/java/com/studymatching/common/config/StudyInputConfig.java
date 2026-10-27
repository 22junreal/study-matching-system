package com.studymatching.common.config;
import com.studymatching.study.entity.StudyCategory;
import com.studymatching.study.entity.StudyLevel;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class StudyInputConfig implements WebMvcConfigurer {
    @Override public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(String.class, StudyCategory.class, StudyCategory::parse);
        registry.addConverter(String.class, StudyLevel.class, StudyLevel::parse);
    }
}
