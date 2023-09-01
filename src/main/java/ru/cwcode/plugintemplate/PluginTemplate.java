package ru.cwcode.plugintemplate;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import tkachgeek.config.yaml.YmlConfig;
import tkachgeek.config.yaml.YmlConfigManager;
import tkachgeek.tkachutils.bootstrap.Bootstrap;
import tkachgeek.tkachutils.reflection.ClassScanner;
import tkachgeek.tkachutils.reflection.ReflectionUtils;
import tkachgeek.tkachutils.reflection.injector.InjectFields;
import tkachgeek.tkachutils.reflection.injector.Injector;
import tkachgeek.tkachutils.scheduler.Tasks;
import tkachgeek.tkachutils.scheduler.annotationRepeatable.Repeat;
import tkachgeek.tkachutils.scheduler.annotationRepeatable.RepeatAPI;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public abstract class PluginTemplate extends Bootstrap {
  public static YmlConfigManager yml;
  public static JavaPlugin plugin;
  public static Logger logger;
  protected volatile InjectFields injectFields;
  protected volatile List<Runnable> runSync = new ArrayList<>();
  
  @Override
  public void onDisable() {
    yml.storeAll();
    Tasks.cancelTasks(this);
  }
  
  @Override
  public void onLoad() {
    plugin = this;
    logger = getLogger();
    yml = new YmlConfigManager(this);
    
    injectFields = new InjectFields();
    
    injectFields.bind(logger, Logger.class);
    injectFields.bind(plugin, JavaPlugin.class);
    
    onInitialize();
    
    super.onLoad();
  }
  
  @Override
  public void onEnable() {
    super.onEnable();
    
    beforeSyncDelayedTasks();
    
    runSync.forEach(Runnable::run);
    runSync = null;
    
    afterSyncDelayedTasks();
  }
  protected void onInitialize() {
  
  }
  
  @Override
  protected CompletableFuture<Void> asyncTask() {
    return CompletableFuture.runAsync(this::scanClasses);
  }
  
  protected void scanClasses() {
    new ClassScanner(this.getFile())
       .apply(new ClassScanner.ClassApplier(YmlConfig.class::isAssignableFrom,
                                            classInfo -> ReflectionUtils.tryToInvokeStaticMethod(classInfo, "getInstance")))
       
       .apply(new ClassScanner.ClassApplier(Listener.class::isAssignableFrom,
                                            this::registerListener))
       
       .apply(new ClassScanner.ClassApplier(classInfo -> true,
                                            classInfo -> runSync.add(() -> Injector.inject(classInfo, injectFields))))
       
       .apply(new ClassScanner.MethodApplier(method -> Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0,
                                             this::handleRepeatMethod))
       
       .scan(this);
  }
  
  protected void registerListener(Class<?> classInfo) {
    Object listener = ReflectionUtils.getNewInstance(classInfo);
    if (listener == null) return;
    
    runSync.add(() -> {
      Bukkit.getPluginManager().registerEvents((Listener) listener, this);
    });
  }
  
  protected void handleRepeatMethod(Method method) {
    Repeat annotation = method.getAnnotation(Repeat.class);
    
    if (annotation != null) {
      runSync.add(() -> {
        this.getLogger().info("Registered task " + method.getDeclaringClass().getSimpleName() + "/" + method.getName());
        RepeatAPI.registerRepeatable(this, method, annotation);
      });
    }
  }
  

  
  protected void afterSyncDelayedTasks() {
  }
  
  protected void beforeSyncDelayedTasks() {
  
  }
}
