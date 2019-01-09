/*
 * Copyright (C) 2007-2019 Crafter Software Corporation. All Rights Reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.craftercms.deployer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.io.CompositeTemplateLoader;
import com.github.jknack.handlebars.springmvc.SpringTemplateLoader;
import freemarker.template.TemplateException;
import org.craftercms.core.cache.impl.CacheStoreAdapter;
import org.craftercms.core.cache.impl.store.NoopCacheStoreAdapter;
import org.craftercms.deployer.api.TargetService;
import org.craftercms.deployer.impl.ProcessedCommitsStore;
import org.craftercms.deployer.impl.ProcessedCommitsStoreImpl;
import org.craftercms.deployer.utils.handlebars.ListHelper;
import org.craftercms.deployer.utils.handlebars.MissingValueHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.io.IOException;

import static org.craftercms.deployer.DeployerApplication.CORE_APP_CONTEXT_LOCATION;

/**
 * Launcher class and Spring configuration entry point.
 *
 * @author avasquez
 */
@SpringBootApplication
@EnableScheduling
@ImportResource(CORE_APP_CONTEXT_LOCATION)
public class DeployerApplication implements WebMvcConfigurer {

	private static final Logger logger = LoggerFactory.getLogger(DeployerApplication.class);

	public static final String CORE_APP_CONTEXT_LOCATION = "classpath:crafter/core/core-context.xml";

	@Value("${deployer.main.targets.scan.scheduling.enabled}")
	private boolean scheduledTargetScanEnabled;
	@Value("${deployer.main.targets.scan.scheduling.cron}")
	private String scheduledTargetScanCron;
	@Value("${deployer.main.taskScheduler.poolSize}")
	private int taskSchedulerPoolSize;
	@Value("${deployer.main.targets.config.templates.location}")
	private String targetConfigTemplatesLocation;
	@Value("${deployer.main.targets.config.templates.overrideLocation}")
	private String targetConfigTemplatesOverrideLocation;
	@Value("${deployer.main.targets.config.templates.suffix}")
	private String targetConfigTemplatesSuffix;
	@Value("${deployer.main.targets.config.templates.encoding}")
	private String targetConfigTemplatesEncoding;
	@Value("${deployer.main.deployments.processedCommits.folderPath}")
	private File processedCommitsFolder;
	@Autowired
	private TargetService targetService;

	public static void main(String[] args) {
		SpringApplication.run(DeployerApplication.class, args);
	}

	/**
	 * Overwrites the default Crafter Core cache store adapter.
	 */
	@Bean("crafter.cacheStoreAdapter")
	public CacheStoreAdapter cacheStoreAdapter() {
		return new NoopCacheStoreAdapter();
	}

	@Bean
	public ProcessedCommitsStore processedCommitsStore() {
		ProcessedCommitsStoreImpl store = new ProcessedCommitsStoreImpl();
		store.setStoreFolder(processedCommitsFolder);

		return store;
	}

	@Bean
	@Primary
	public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
		ObjectMapper objectMapper = builder.createXmlMapper(false).build();
		objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
		return objectMapper;
	}

	@Bean(destroyMethod="shutdown")
	public ThreadPoolTaskScheduler taskScheduler() {
		ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
		taskScheduler.setPoolSize(taskSchedulerPoolSize);

		return taskScheduler;
	}

	@Bean
	public Handlebars targetConfigTemplateEngine(ResourceLoader resourceLoader) throws IOException, TemplateException {
		SpringTemplateLoader templateOverridesLoader = new SpringTemplateLoader(resourceLoader);
		templateOverridesLoader.setPrefix(targetConfigTemplatesOverrideLocation);
		templateOverridesLoader.setSuffix(targetConfigTemplatesSuffix);

		SpringTemplateLoader templateLoader = new SpringTemplateLoader(resourceLoader);
		templateLoader.setPrefix(targetConfigTemplatesLocation);
		templateLoader.setSuffix(targetConfigTemplatesSuffix);

		CompositeTemplateLoader compositeTemplateLoader = new CompositeTemplateLoader(templateOverridesLoader, templateLoader);

		Handlebars handlebars = new Handlebars(compositeTemplateLoader);
		handlebars.prettyPrint(true);

		handlebars.registerHelper(ListHelper.NAME, ListHelper.INSTANCE);
		handlebars.registerHelperMissing(MissingValueHelper.INSTANCE);

		return handlebars;
	}

	@Override
	public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
		configurer.defaultContentType(MediaType.APPLICATION_JSON_UTF8);
	}

}
