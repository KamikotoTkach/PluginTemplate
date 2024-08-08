package ru.cwcode.tkach.plugintemplate;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import ru.cwcode.commands.Command;
import ru.cwcode.cwutils.ReloadCatcher;
import ru.cwcode.cwutils.bootstrap.Bootstrap;
import ru.cwcode.cwutils.dependencyChecker.DependencyChecker;
import ru.cwcode.cwutils.dependencyChecker.PluginRequirement;
import ru.cwcode.cwutils.reflection.ClassScanner;
import ru.cwcode.cwutils.reflection.ReflectionUtils;
import ru.cwcode.cwutils.reflection.injector.Inject;
import ru.cwcode.cwutils.reflection.injector.InjectFields;
import ru.cwcode.cwutils.reflection.injector.Injector;
import ru.cwcode.cwutils.scheduler.Tasks;
import ru.cwcode.cwutils.scheduler.annotationRepeatable.Repeat;
import ru.cwcode.cwutils.scheduler.annotationRepeatable.RepeatAPI;
import ru.cwcode.tkach.config.base.Config;
import ru.cwcode.tkach.plugintemplate.annotations.DoNotRegister;
import ru.cwcode.tkach.config.commands.ReloadCommands;
import ru.cwcode.tkach.config.jackson.yaml.YmlConfigManager;
import ru.cwcode.tkach.config.paper.PaperPluginConfigPlatform;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

public abstract class PluginTemplate extends Bootstrap {
  public static YmlConfigManager yml;
  public static JavaPlugin plugin;
  public static Logger logger;
  
  protected volatile InjectFields injectFields = new InjectFields();
  protected volatile List<Runnable> runSync = new ArrayList<>();
  protected volatile List<Class<?>> allClasses = new ArrayList<>();
  protected volatile List<Method> handleRepeatable = new ArrayList<>();
  
  protected DependencyChecker dependencyChecker = new DependencyChecker();
  
  protected boolean debug = false;
  
  public void debug(Supplier<String> log) {
    if (debug) logger.info(log.get());
  }
  
  @Override
  public void onDisable() {
    debug(() -> "PreDisable");
    
    yml.saveAll(configPersistOptions -> {});
    Tasks.cancelTasks(this);
    
    debug(() -> "PostDisable");
  }
  
  @Override
  public void onLoad() {
    if (this.getDescription().getVersion().toLowerCase().contains("debug")) {
      debug = true;
    }
    
    logger = getLogger();
    
    debug(() -> "PreLoad");
    
    plugin = this;
    yml = new YmlConfigManager(getConfigPlatform());
    
    bind(Logger.class, logger);
    bind(JavaPlugin.class, plugin);
    
    dependencyChecker.addListener(new DependencyAdapterListener(this));
    
    super.onLoad();
    
    debug(() -> "PostLoad");
    
  }
  
  protected @NotNull PaperPluginConfigPlatform getConfigPlatform() {
    return new PaperPluginConfigPlatform(this);
  }
  
  @Override
  protected CompletableFuture<Void> asyncTask() {
    return CompletableFuture.runAsync(this::scanClasses);
  }
  
  @Override
  public void onEnable() {
    debug(() -> "PreEnable");
    
    dependencyChecker.handleDependencies();
    super.onEnable();
    
    Bukkit.getPluginManager().registerEvents(new ReloadCatcher(),this);
    
    runDelayedTasks();
  }
  
  public void addDependency(String pluginId, Consumer<PluginRequirement.PluginRequirementBuilder> configurer) {
    dependencyChecker.addDependency(pluginId, configurer);
  }
  
  private void runDelayedTasks() {
    debug(() -> "PostEnable, PreDelayedTasks");
    
    onDelayedTask();
    
    allClasses.stream()
              .filter(x -> x.isAnnotationPresent(Inject.class))
              .peek(o -> debug(() -> o.getSimpleName() + " auto-bind candidate"))
              .map(ReflectionUtils::getNewInstance)
              .filter(Objects::nonNull)
              .peek(o -> debug(() -> o.getClass().getSimpleName() + " was auto-binded"))
              .forEach(this::bind);
    
    allClasses.stream()
              .filter(Config.class::isAssignableFrom)
              .peek(configClass -> {
                debug(() -> configClass.getSimpleName() + " was injected");
                Injector.inject(configClass, injectFields);
              }) //чтобы перед созданием конфига у него уже были прокинуты зависимости
              .map(PluginTemplate::getInstanceReflection)
              .peek(o -> debug(() -> o == null ? "~null" : o.getClass().getSimpleName() + " was auto-binded"))
              .filter(Objects::nonNull)
              .forEach(this::bind);
    
    for (Class<?> classInfo : allClasses) {
      if (Listener.class.isAssignableFrom(classInfo)) {
        registerListener(classInfo);
      }
      
      Injector.inject(classInfo, injectFields);
    }
    
    handleRepeatable.forEach(this::handleRepeatMethod);
    runSync.forEach(Runnable::run);
    
    runSync = null;
    injectFields = null;
    handleRepeatable = null;
    dependencyChecker = null;
    
    debug(() -> "PostDelayedTasks");
  }
  
  protected  <T> void bind(Class<T> clazz, T object) {
    injectFields.bind(object, clazz);
    
    debug(() -> "Binded class " + object.getClass() + " as " + clazz);
  }
  
  protected void bind(Object o) {
    Class<Object> clazz = (Class<Object>) o.getClass();
    
    injectFields.bind(o, clazz);
    
    debug(() -> "[o] Binded class " + clazz);
  }
  
  private static <T> T getInstanceReflection(Class<T> clazz) {
    try {
      Method load = clazz.getDeclaredMethod("getInstance");
      if (Modifier.isStatic(load.getModifiers()) && load.getParameterCount() == 0) {
        return (T) load.invoke(null);
      }
    } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | ClassCastException e) {
      e.printStackTrace();
    }
    return null;
  }
  
  protected void onDelayedTask() {
  }
  
  private void registerListener(Class<?> classInfo) {
    debug(() -> "Trying to register listener "+ classInfo.getSimpleName());
    
    if (classInfo.isAnnotationPresent(DoNotRegister.class)) return;
    
    Object listener = ReflectionUtils.getNewInstance(classInfo);
    if (listener == null) return;
    
    Bukkit.getPluginManager().registerEvents((Listener) listener, this);
    
    debug(() -> "Listener "+ classInfo.getSimpleName() + " was registered");
  }
  
  private void handleRepeatMethod(Method method) {
    String name = method.getDeclaringClass().getSimpleName() + "/" + method.getName();
      
    debug(() -> "Handling repeat method "+ name);
    
    Repeat annotation = method.getAnnotation(Repeat.class);
    
    if (annotation != null) {
      this.getLogger().info("Registered task " + name);
      RepeatAPI.registerRepeatable(this, method, annotation);
      
      debug(() -> "Repeat method " + name + " registered");
    }
  }
  
  private void scanClasses() {
    debug(() -> "Scanning classes");
    
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
    debug(() -> "Updating injected fields ");
    
    InjectFields inject = new InjectFields(update.toArray());
    
    for (Class<?> classInfo : allClasses) {
      Injector.inject(classInfo, inject);
      
      debug(() -> "Class " + classInfo.getSimpleName() + " was injected");
    }
  }
  
  protected Command paperReload() {
    return ReloadCommands.get(yml, ymlConfig -> {
      updateInjectedFields(Collections.singletonList(ymlConfig));
    });
  }
}
