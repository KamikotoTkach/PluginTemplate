package ru.cwcode.tkach.plugintemplate;

import ru.cwcode.tkach.dependencychecker.DependencyListener;

public class DependencyAdapterListener implements DependencyListener {
  PluginTemplate pluginTemplate;
  
  public DependencyAdapterListener(PluginTemplate pluginTemplate) {
    this.pluginTemplate = pluginTemplate;
  }
  
  @Override
  public void handleAdapter(Class<?> clazz, Object adapter) {
    pluginTemplate.bind((Class<? super Object>) clazz, adapter);
  }
}
