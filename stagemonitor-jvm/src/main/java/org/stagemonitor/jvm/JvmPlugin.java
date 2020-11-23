package org.stagemonitor.jvm;


import com.codahale.metrics.Gauge;
import com.codahale.metrics.jvm.BufferPoolMetricSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.stagemonitor.core.CorePlugin;
import org.stagemonitor.core.StagemonitorPlugin;
import org.stagemonitor.core.metrics.metrics2.Metric2Registry;
import org.stagemonitor.core.metrics.metrics2.Metric2Set;
import org.stagemonitor.core.metrics.metrics2.MetricName;
import org.stagemonitor.core.metrics.metrics2.MetricNameConverter;

import javax.management.MBeanServer;
import java.lang.management.ManagementFactory;

import static org.stagemonitor.core.metrics.metrics2.MetricName.name;

public class JvmPlugin extends StagemonitorPlugin {
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

	@Override
	public void initializePlugin(StagemonitorPlugin.InitArguments initArguments) {
		initArguments.getMetricRegistry().registerAll(Metric2Set.Converter.convert(new GarbageCollectorMetricSet(), new MetricNameConverter() {
			@Override
			public MetricName convert(String name) {
				final String[] split = name.split("\\.");
				return name("jvm_gc_" + split[1]).tag("collector", split[0]).build();
			}
		}));
		initArguments.getMetricRegistry().registerAll(Metric2Set.Converter.convert(new MemoryUsageGaugeSet(), new MetricNameConverter() {
			@Override
			public MetricName convert(String name) {
				final String[] split = name.split("\\.");
				if (split.length == 3) {
					return name("jvm_memory_" + split[0]).tag("memory_pool", split[1]).type(split[2]).build();
				}
				return name("jvm_memory_" + split[0].replace('-', '_')).type(split[1]).build();
			}
		}));

		// add nio memory BufferPool  java.nio:type=BufferPool
		initArguments.getMetricRegistry().registerAll(Metric2Set.Converter.convert(new BufferPoolMetricSet(server), new MetricNameConverter() {
			@Override
			public MetricName convert(String name) {
				final String[] split = name.split("\\.");
				return name("nio_bufferPool_" + split[0].replace('-', '_')).type(split[1]).build();
			}
		}));

		//add JVM thread
		Metric2Registry registry = initArguments.getMetricRegistry();
		registry.register(name("jvm_threads").type("count").build(), new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return ManagementFactory.getThreadMXBean().getThreadCount();
			}
		});
		registry.register(name("jvm_threads").type("peakCount").build(), new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return ManagementFactory.getThreadMXBean().getPeakThreadCount();
			}
		});
		registry.register(name("jvm_threads").type("totalStartedCount").build(), new Gauge<Long>() {
			@Override
			public Long getValue() {
				return ManagementFactory.getThreadMXBean().getTotalStartedThreadCount();
			}
		});
		registry.register(name("jvm_threads").type("daemonCount").build(), new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return ManagementFactory.getThreadMXBean().getDaemonThreadCount();
			}
		});

		//add JVM class loaded
		registry.register(name("jvm_classes").type("loadedCount").build(), new Gauge<Integer>() {
			@Override
			public Integer getValue() {
				return ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
			}
		});
		registry.register(name("jvm_classes").type("totalLoadedCount").build(), new Gauge<Long>() {
			@Override
			public Long getValue() {
				return ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
			}
		});
		registry.register(name("jvm_classes").type("unloadedCount").build(), new Gauge<Long>() {
			@Override
			public Long getValue() {
				return ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
			}
		});


		final CpuUtilisationWatch cpuWatch;
		try {
			cpuWatch = new CpuUtilisationWatch();
			cpuWatch.start();
			initArguments.getMetricRegistry().register(name("jvm_process_cpu_usage").build(), new Gauge<Float>() {
				@Override
				public Float getValue() {
					try {
						return cpuWatch.getCpuUsagePercent() * 100F;
					} finally {
						cpuWatch.start();
					}
				}
			});
		} catch (ClassNotFoundException cnfe) {
			logger.warn("Could not register cpu usage due to ClassNotFoundException. Please make sure that com.sun.management.OperatingSystemMXBean can be loaded.");
		} catch (Exception e) {
			logger.warn("Could not register cpu usage. ({})", e.getMessage());
		}

		final CorePlugin corePlugin = initArguments.getPlugin(CorePlugin.class);
		corePlugin.getGrafanaClient().sendGrafanaDashboardAsync("grafana/ElasticsearchJvm.json");
	}

	@Override
	public void registerWidgetMetricTabPlugins(WidgetMetricTabPluginsRegistry widgetMetricTabPluginsRegistry) {
		widgetMetricTabPluginsRegistry.addWidgetMetricTabPlugin("/stagemonitor/static/tabs/metrics/jvm-metrics");
	}

}
