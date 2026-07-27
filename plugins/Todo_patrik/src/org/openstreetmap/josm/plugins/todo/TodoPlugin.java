package org.openstreetmap.josm.plugins.todo;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class TodoPlugin extends Plugin {
   private TodoDialog dialog;

   public TodoPlugin(PluginInformation var1) {
      super(var1);
   }

   public void mapFrameInitialized(MapFrame var1, MapFrame var2) {
      if (var1 == null && var2 != null) {
         this.dialog = new TodoDialog();
         var2.addToggleDialog(this.dialog);
      }
   }
}
