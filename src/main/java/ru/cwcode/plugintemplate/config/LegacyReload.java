package ru.cwcode.plugintemplate.config;

import net.kyori.adventure.audience.Audience;
import ru.cwcode.plugintemplate.PluginTemplate;
import tkachgeek.commands.command.ArgumentSet;
import tkachgeek.commands.command.Command;
import tkachgeek.commands.command.arguments.ExactStringArg;
import tkachgeek.config.base.Config;
import tkachgeek.config.yaml.ConfigsArg;
import tkachgeek.config.yaml.ReloadCommand;
import tkachgeek.config.yaml.YmlConfigManager;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LegacyReload {
  
  private final YmlConfigManager manager;
  private final PluginTemplate plugin;
  
  public LegacyReload(YmlConfigManager yml, PluginTemplate plugin) {
    this.manager = yml;
    this.plugin = plugin;
  }
  
  public Command get() {
    return new Command("reload", "$*").arguments(
       new ArgumentSet(new ConfigReload(manager, plugin), "", new ConfigsArg(manager)),
       new ArgumentSet(new ConfigReloadAll(manager, plugin), "", new ExactStringArg("all"))
    );
  }
  
  public static class ConfigReload extends ReloadCommand.ConfigReload {
    private final PluginTemplate plugin;
    
    public ConfigReload(YmlConfigManager manager, PluginTemplate plugin) {
      super(manager);
      this.plugin = plugin;
    }
    
    @Override
    public void executeForPlayer() {
      Audience audience = this.sender();
      Logger.getLogger(sender.getName()).log(Level.INFO, "Инициировал перезагрузку конфига " + this.argS(0));
      
      Config config = this.manager.reloadByCommand(this.argS(0), audience);
      
      if (config != null) plugin.updateInjectedFields(Collections.singletonList(config));
    }
  }
  
  public static class ConfigReloadAll extends ReloadCommand.ConfigReloadAll {
    private final PluginTemplate plugin;
    
    public ConfigReloadAll(YmlConfigManager manager, PluginTemplate plugin) {
      super(manager);
      this.plugin = plugin;
    }
    
    @Override
    public void executeForPlayer() {
      Logger.getLogger(sender.getName()).log(Level.INFO, "Инициировал перезагрузку конфигов");
      
      List<Config> reloaded = this.manager.reloadByCommand(sender);
      
      plugin.updateInjectedFields(reloaded);
    }
  }
}
