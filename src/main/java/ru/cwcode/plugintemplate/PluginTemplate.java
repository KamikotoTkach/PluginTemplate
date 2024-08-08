package ru.cwcode.plugintemplate;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import ru.cwcode.plugintemplate.annotations.DoNotRegister;
import ru.cwcode.plugintemplate.config.PaperReload;
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
  protected volatile InjectFields injectFields = new InjectFields();
  protected volatile List<Runnable> runSync = new ArrayList<>();
  protected volatile List<Class<?>> allClasses = new ArrayList<>();
  protected volatile List<Method> handleRepeatable = new ArrayList<>();
  
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
    
    injectFields.bind(logger, Logger.class);
    injectFields.bind(plugin, JavaPlugin.class);
    
    super.onLoad();
  }
  
  @Override
  protected CompletableFuture<Void> asyncTask() {
    return CompletableFuture.runAsync(this::scanClasses);
  }
  
  @Override
  public void onEnable() {
    super.onEnable();
    
    runDelayedTasks();
  }
  
  private void runDelayedTasks() {
    onDelayedTask();
    
    for (Class<?> classInfo : allClasses) {
      if (Listener.class.isAssignableFrom(classInfo)) {
        registerListener(classInfo);
      }
      
      if (YmlConfig.class.isAssignableFrom(classInfo)) {
        ReflectionUtils.tryToInvokeStaticMethod(classInfo, "getInstance");
      }
      
      Injector.inject(classInfo, injectFields);
    }
    
    for (Method method : handleRepeatable) {
      handleRepeatMethod(method);
    }
    
    for (Runnable runnable : runSync) {
      runnable.run();
    }
    
    runSync = null;
    injectFields = null;
    handleRepeatable = null;
  }
  
  protected void onDelayedTask() {
  }
  
  private void registerListener(Class<?> classInfo) {
    if (classInfo.isAnnotationPresent(DoNotRegister.class)) return;
    
    Object listener = ReflectionUtils.getNewInstance(classInfo);
    if (listener == null) return;
    
    Bukkit.getPluginManager().registerEvents((Listener) listener, this);
  }
  
  private void handleRepeatMethod(Method method) {
    Repeat annotation = method.getAnnotation(Repeat.class);
    
    if (annotation != null) {
      this.getLogger().info("Registered task " + method.getDeclaringClass().getSimpleName() + "/" + method.getName());
      RepeatAPI.registerRepeatable(this, method, annotation);
    }
  }
  
  private void scanClasses() {
    try {
      
      new ClassScanner(this.getFile())
         .apply(new ClassScanner.ClassApplier(aClass -> true,
                                              classInfo -> allClasses.add(classInfo)))
         
         .apply(new ClassScanner.MethodApplier(method -> Modifier.isStatic(method.getModifiers()) && method.getParameterCount() == 0,
                                               method -> handleRepeatable.add(method)))
         
         .classFilter(classPath -> !classPath.startsWith(getClass().getPackage().getName() + ".integration."))
         .scan(this);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  public void updateInjectedFields(List<?> update) {
    InjectFields inject = new InjectFields(update.toArray());
    
    for (Class<?> classInfo : allClasses) {
      Injector.inject(classInfo, inject);
    }
  }
  
  protected PaperReload paperReload() {
    return new PaperReload(yml, this);
  }
}
