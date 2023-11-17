package ru.cwcode.plugintemplate.config;

import net.kyori.adventure.audience.Audience;
import org.bukkit.command.CommandSender;
import ru.cwcode.commands.ArgumentSet;
import ru.cwcode.commands.Command;
import ru.cwcode.commands.arguments.ExactStringArg;
import ru.cwcode.plugintemplate.PluginTemplate;
import tkachgeek.config.base.Config;
import tkachgeek.config.yaml.YmlConfigManager;
import tkachgeek.config.yaml.newCommands.PaperConfigsArg;
import tkachgeek.config.yaml.newCommands.PaperReloadCommand;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaperReload {
  private final YmlConfigManager manager;
  private final PluginTemplate plugin;
  
  public PaperReload(YmlConfigManager yml, PluginTemplate plugin) {
    this.manager = yml;
    this.plugin = plugin;
  }
  
  public Command get() {
    return new Command("reload", "$*").arguments(
       new ArgumentSet(new ConfigReload(manager, plugin), "", new PaperConfigsArg(manager)),
       new ArgumentSet(new ConfigReloadAll(manager, plugin), "", new ExactStringArg("all"))
    );
  }
  public static class ConfigReload extends PaperReloadCommand.ConfigReload {
    private final PluginTemplate plugin;
    
    public ConfigReload(YmlConfigManager manager, PluginTemplate plugin) {
      super(manager);
      this.plugin = plugin;
    }
    
    @Override
    public void executeForPlayer() {
      Audience audience = this.sender();
      if (audience instanceof CommandSender) {
        Logger.getLogger(((CommandSender)audience).getName()).log(Level.INFO, "Инициировал перезагрузку конфига " + this.argS(0));
      }
      
      Config config = this.manager.reloadByCommand(this.argS(0), audience);
      
      if(config!= null) plugin.updateInjectedFields(Collections.singletonList(config));
    }
  }
  public static class ConfigReloadAll extends PaperReloadCommand.ConfigReloadAll {
    private final PluginTemplate plugin;
    
    public ConfigReloadAll(YmlConfigManager manager, PluginTemplate plugin) {
      super(manager);
      this.plugin = plugin;
    }
    
    @Override
    public void executeForPlayer() {
      Audience audience = this.sender.getAudience();
      if (audience instanceof CommandSender) {
        Logger.getLogger(((CommandSender)audience).getName()).log(Level.INFO, "Инициировал перезагрузку конфигов");
      }
      
      List<Config> reloaded = this.manager.reloadByCommand(audience);
      
      plugin.updateInjectedFields(reloaded);
    }
  }
}
