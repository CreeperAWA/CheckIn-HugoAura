package indi.etern.checkIn;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableAsync
public class CheckInApplication {

	public static void main(String[] args) {
		SpringApplication.run(CheckInApplication.class, args);
	}
	
	@Getter
	private static ObjectMapper objectMapper;
	
	@Bean
	public ObjectMapper objectMapper(){
		objectMapper = new ObjectMapper();
		objectMapper.registerModule(new JavaTimeModule());
		StreamReadConstraints.overrideDefaultStreamReadConstraints(
				StreamReadConstraints.builder().maxStringLength(64 * 1024 * 1024).build()
		);
		return objectMapper;
	}
	
	@Bean
	public Cache<?, Object> caffeineCache() {
		return Caffeine.newBuilder()
				.expireAfterWrite(30, TimeUnit.SECONDS)
				.initialCapacity(128)
				.maximumSize(8192)
				.build();
	}
	
	@Bean("scoreRecalculationExecutor")
	public Executor scoreRecalculationExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(4);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("score-recalc-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}
}
