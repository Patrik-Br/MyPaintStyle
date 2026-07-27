package org.openstreetmap.josm.plugins.todo;

import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionChangeEvent;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.AbstractModifiableLayer;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Shortcut;

public class TodoDialog extends ToggleDialog implements LayerChangeListener {
   private static final long serialVersionUID = 1L;
   private static TodoDialog instance;
   private final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
   final TodoListModel model = new TodoListModel(this.selectionModel);
   private final JList<TodoListItem> lstPrimitives = new JList<>(this.model);
   private SideButton selectButton;

   public static TodoDialog getInstance() {
      return instance;
   }

   public void addItemsFromPrimitives(Collection<? extends IPrimitive> var1) {
      GuiHelper.runInEDT(() -> this.runWithPrototype(() -> this.model.addItems(var1)));
   }

   public TodoDialog() {
      super(
         I18n.tr("Todo list", new Object[0]),
         "todo",
         I18n.tr("Open the todo list.", new Object[0]),
         Shortcut.registerShortcut("subwindow:todo", I18n.tr("Toggle: {0}", new Object[]{I18n.tr("Todo list", new Object[0])}), 65535, 5000),
         150,
         true
      );
      this.buildContentPanel();
      instance = this;
      this.model.addListDataListener(new TodoDialog.TitleUpdater());
      MainApplication.getLayerManager().addLayerChangeListener(this);
      DatasetEventManager.getInstance().addDatasetListener(this.model, FireMode.IN_EDT_CONSOLIDATED);
   }

   Collection<? extends IPrimitive> getItems() {
      OsmData var1 = MainApplication.getLayerManager().getActiveData();
      return (Collection<? extends IPrimitive>)(var1 == null ? Collections.emptyList() : var1.getSelected());
   }

   void runWithPrototype(Runnable var1) {
      this.lstPrimitives.setPrototypeCellValue(new TodoListItem(null, null) {
         @Override
         public String toString() {
            return "XXXXXXXXXXXXXXXXXXXXXXXX";
         }
      });
      var1.run();
      this.lstPrimitives.setPrototypeCellValue(null);
   }

   private void buildContentPanel() {
      this.lstPrimitives.setSelectionModel(this.selectionModel);
      this.lstPrimitives.setSelectionMode(2);
      this.lstPrimitives.setCellRenderer(new TodoListItemRenderer());
      this.lstPrimitives.addMouseListener(new TodoDialog.DblClickHandler());
      TodoDialog.AddAction var1 = new TodoDialog.AddAction();
      TodoDialog.AddAndZoomAction var2 = new TodoDialog.AddAndZoomAction();
      TodoDialog.SelectAction var3 = new TodoDialog.SelectAction();
      TodoDialog.PassAction var4 = new TodoDialog.PassAction();
      TodoDialog.MarkAction var5 = new TodoDialog.MarkAction();
      TodoDialog.MarkSelectedAction var6 = new TodoDialog.MarkSelectedAction();
      TodoDialog.UnmarkAction var7 = new TodoDialog.UnmarkAction();
      TodoDialog.ClearAndAddAction var8 = new TodoDialog.ClearAndAddAction();
      TodoDialog.MarkAllAction var9 = new TodoDialog.MarkAllAction();
      TodoDialog.UnmarkAllAction var10 = new TodoDialog.UnmarkAllAction();
      TodoDialog.ClearAction var11 = new TodoDialog.ClearAction();
      TodoDialog.SelectItemsAction var13 = new TodoDialog.SelectItemsAction();
      this.selectButton = new SideButton(var3);
      this.selectionModel.addListSelectionListener(var3);
      this.selectionModel.addListSelectionListener(var4);
      this.selectionModel.addListSelectionListener(var5);
      this.selectionModel.addListSelectionListener(var7);
      this.selectionModel.addListSelectionListener(var13);
      this.addSelectionListenerForEdt(var1);
      this.addSelectionListenerForEdt(var2);
      this.addSelectionListenerForEdt(var6);
      this.addSelectionListenerForEdt(var8);
      JPopupMenu var12 = new JPopupMenu();
      var12.add(new JMenuItem(var13));
      var12.add(new JMenuItem(var6));
      var12.add(new JMenuItem(var7));
      var12.add(new JMenuItem(var9));
      var12.add(new JMenuItem(var10));
      var12.add(new JMenuItem(var11));
      this.lstPrimitives.addMouseListener(new TodoDialog.TodoPopupLauncher(var12));
      this.selectionModel.addListSelectionListener(var1x -> {
         if (!var1x.getValueIsAdjusting()) {
            int var2x = this.selectionModel.getMinSelectionIndex();
            if (var2x >= 0) {
               this.lstPrimitives.ensureIndexIsVisible(var2x);
            }
         }
      });
      this.createLayout(
         this.lstPrimitives,
         true,
         Arrays.asList(new SideButton(var1), new SideButton(var2), this.selectButton, new SideButton(var4), new SideButton(var5), new SideButton(var6))
      );
   }

   private void addSelectionListenerForEdt(DataSelectionListener var1) {
      OsmData var2 = MainApplication.getLayerManager().getActiveData();
      if (var2 != null) {
         var2.addSelectionListener(var1);
      }

      MainApplication.getLayerManager().addActiveLayerChangeListener(var1x -> {
         DataSet var2x = var1x.getPreviousDataLayer() != null ? var1x.getPreviousDataLayer().getDataSet() : null;
         OsmData var3 = var1x.getSource().getActiveData();
         if (var2x != null) {
            var2x.removeSelectionListener(var1);
         }

         if (var3 != null) {
            var3.addSelectionListener(var1);
         }

         var1.selectionChanged(null);
      });
   }

   void selectAndZoom(Collection<TodoListItem> var1) {
      if (var1 != null && !var1.isEmpty()) {
         TodoListItem var2 = (TodoListItem)var1.iterator().next();
         Layer var3 = MainApplication.getLayerManager().getActiveLayer();
         MainApplication.getLayerManager().setActiveLayer(var2.layer);
         var2.layer.getDataSet().setSelected(Collections.singletonList(var2.primitive));
         AutoScaleAction.zoomToSelection();
         if (var3 != null && var3 != var2.layer) {
            MainApplication.getLayerManager().setActiveLayer(var3);
         }
      }
   }

   void selectItems(Collection<TodoListItem> var1) {
      if (var1 != null && !var1.isEmpty()) {
         Map<AbstractOsmDataLayer, List<IPrimitive>> var2 = new LinkedHashMap<>();

         for (TodoListItem var4 : var1) {
            if (var4.layer != null && var4.primitive != null) {
               var2.computeIfAbsent(var4.layer, var5 -> new ArrayList<>()).add(var4.primitive);
            }
         }

         for (Map.Entry<AbstractOsmDataLayer, List<IPrimitive>> var4 : var2.entrySet()) {
            if (var4.getKey().getDataSet() != null) {
               var4.getKey().getDataSet().setSelected(var4.getValue());
            }
         }
      }
   }

   public void layerAdded(LayerAddEvent var1) {
   }

   public void layerOrderChanged(LayerOrderChangeEvent var1) {
   }

   public void layerRemoving(LayerRemoveEvent var1) {
      if (var1.getRemovedLayer() instanceof AbstractModifiableLayer) {
         this.model.purgeLayerItems((AbstractModifiableLayer)var1.getRemovedLayer());
      }
   }

   public void destroy() {
      super.destroy();
      instance = null;
      MainApplication.getLayerManager().removeLayerChangeListener(this);
      DatasetEventManager.getInstance().removeDatasetListener(this.model);
   }

   class AddAction extends JosmAction implements DataSelectionListener {
      private static final long serialVersionUID = 1L;

      AddAction() {
         super(
            I18n.tr("Add", new Object[0]),
            "dialogs/add",
            I18n.tr("Add the selected items to the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:add", I18n.tr("Todo list: Add", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.runWithPrototype(() -> {
            TodoDialog.this.model.clearAll();
            TodoDialog.this.model.addItems(TodoDialog.this.getItems());
         });
      }

      public void selectionChanged(SelectionChangeEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         OsmData var1 = MainApplication.getLayerManager().getActiveData();
         this.setEnabled(var1 != null && !var1.selectionEmpty());
      }
   }

   class AddAndZoomAction extends JosmAction implements DataSelectionListener {
      private static final long serialVersionUID = 1L;

      AddAndZoomAction() {
         super(
            I18n.tr("Add+Zoom", new Object[0]),
            "dialogs/add",
            I18n.tr("Add the selected items to the todo list and zoom to the first item.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:addzoom", I18n.tr("Todo list: Add and zoom", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.runWithPrototype(() -> {
            TodoDialog.this.model.clearAll();
            TodoDialog.this.model.addItems(TodoDialog.this.getItems());
         });
         List var2 = TodoDialog.this.model.getTodoList();
         if (!var2.isEmpty()) {
            TodoDialog.this.selectAndZoom(Collections.singletonList((TodoListItem)var2.get(0)));
         }
      }

      public void selectionChanged(SelectionChangeEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         OsmData var1 = MainApplication.getLayerManager().getActiveData();
         this.setEnabled(var1 != null && !var1.selectionEmpty());
      }
   }

   class ClearAction extends JosmAction {
      private static final long serialVersionUID = 1L;

      ClearAction() {
         super(
            I18n.tr("Clear the todo list", new Object[0]),
            "dialogs/delete",
            I18n.tr("Remove all items (marked and unmarked) from the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:clear", I18n.tr("Todo list: Clear", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.model.clearAll();
      }
   }

   class ClearAndAddAction extends JosmAction implements DataSelectionListener {
      private static final long serialVersionUID = 1L;

      ClearAndAddAction() {
         super(
            I18n.tr("Clear and add", new Object[0]),
            "dialogs/selectionlist",
            I18n.tr("Clear list, add the selected items to the todo list and zoom first item.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:clearandadd", I18n.tr("Todo list: Clear and add elements", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.runWithPrototype(() -> {
            TodoDialog.this.model.clearAll();
            TodoDialog.this.model.addItems(TodoDialog.this.getItems());
            TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
         });
      }

      public void selectionChanged(SelectionChangeEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         OsmData var1 = MainApplication.getLayerManager().getActiveData();
         this.setEnabled(var1 != null && !var1.selectionEmpty());
      }
   }

   private class DblClickHandler extends MouseAdapter {
      @Override
      public void mouseClicked(MouseEvent var1) {
         if (var1.getClickCount() == 2 && !var1.isPopupTrigger()) {
            TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
         }
      }
   }

   class MarkAction extends JosmAction implements ListSelectionListener {
      private static final long serialVersionUID = 1L;

      MarkAction() {
         super(
            I18n.tr("Mark", new Object[0]),
            "dialogs/check",
            I18n.tr("Mark the selected item in the todo list as done.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:mark", I18n.tr("Todo list: Mark element done", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         Collection var2 = TodoDialog.this.model.getSelected();
         TodoDialog.this.model.markItems(var2);
         TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
      }

      @Override
      public void valueChanged(ListSelectionEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         this.setEnabled(!TodoDialog.this.model.isSelectionEmpty());
      }
   }

   class MarkAllAction extends JosmAction {
      private static final long serialVersionUID = 1L;

      MarkAllAction() {
         super(
            I18n.tr("Mark all", new Object[0]),
            "dialogs/todo",
            I18n.tr("Mark all items in the todo list as done.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:mark_all", I18n.tr("Todo list: Mark all done", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.model.markAll();
      }
   }

   class MarkSelectedAction extends JosmAction implements DataSelectionListener {
      private static final long serialVersionUID = 1L;

      MarkSelectedAction() {
         super(
            I18n.tr("Mark selected", new Object[0]),
            "dialogs/select",
            I18n.tr("Mark the selected items (on the map) as done in the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:mark_selected", I18n.tr("Todo list: Mark selected element done", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.runWithPrototype(() -> {
            Collection var1x = TodoDialog.this.model.getItemsForPrimitives(TodoDialog.this.getItems());
            TodoDialog.this.model.markItems(var1x);
            TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
         });
      }

      public void selectionChanged(SelectionChangeEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         OsmData var1 = MainApplication.getLayerManager().getActiveData();
         this.setEnabled(var1 != null && !var1.selectionEmpty());
      }
   }

   class PassAction extends JosmAction implements ListSelectionListener {
      private static final long serialVersionUID = 1L;

      PassAction() {
         super(
            I18n.tr("Pass", new Object[0]),
            "dialogs/zoom-best-fit",
            I18n.tr("Moves on to the next item but leaves this item in the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:pass", I18n.tr("Todo list: Pass over element without marking it", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.model.incrementSelection();
         TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
      }

      @Override
      public void valueChanged(ListSelectionEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         this.setEnabled(TodoDialog.this.model.getSize() > 0 && !TodoDialog.this.model.isSelectionEmpty());
      }
   }

   class SelectAction extends JosmAction implements ListSelectionListener {
      private static final long serialVersionUID = 1L;

      SelectAction() {
         super(
            I18n.tr("Zoom", new Object[0]),
            "dialogs/zoom-best-fit",
            I18n.tr("Zoom to the selected item in the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:zoom_to_selected_item", I18n.tr("Todo list: Zoom", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.selectAndZoom(TodoDialog.this.model.getSelected());
      }

      @Override
      public void valueChanged(ListSelectionEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         this.setEnabled(!TodoDialog.this.model.isSelectionEmpty());
      }
   }

   class SelectItemsAction extends JosmAction implements ListSelectionListener {
      private static final long serialVersionUID = 1L;

      SelectItemsAction() {
         super(
            I18n.tr("Select", new Object[0]),
            "dialogs/select",
            I18n.tr("Select the highlighted items in the todo list on the map.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:select", I18n.tr("Todo list: Select highlighted items", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.selectItems(TodoDialog.this.model.getSelected());
      }

      @Override
      public void valueChanged(ListSelectionEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         this.setEnabled(!TodoDialog.this.model.isSelectionEmpty());
      }
   }

   private class TitleUpdater implements ListDataListener {
      void update() {
         GuiHelper.runInEDT(() -> TodoDialog.this.setTitle(TodoDialog.this.model.getSummary()));
      }

      @Override
      public void intervalAdded(ListDataEvent var1) {
         this.update();
      }

      @Override
      public void intervalRemoved(ListDataEvent var1) {
         this.update();
      }

      @Override
      public void contentsChanged(ListDataEvent var1) {
         this.update();
      }
   }

   private static class TodoPopupLauncher extends MouseAdapter {
      private final JPopupMenu menu;

      TodoPopupLauncher(JPopupMenu var1) {
         this.menu = var1;
      }

      @Override
      public void mousePressed(MouseEvent var1) {
         this.show(var1);
      }

      @Override
      public void mouseReleased(MouseEvent var1) {
         this.show(var1);
      }

      private void show(MouseEvent var1) {
         if (var1.isPopupTrigger()) {
            this.menu.show(var1.getComponent(), var1.getX(), var1.getY());
         }
      }
   }

   class UnmarkAction extends JosmAction implements ListSelectionListener {
      private static final long serialVersionUID = 1L;

      UnmarkAction() {
         super(
            I18n.tr("Unmark", new Object[0]),
            "dialogs/refresh",
            I18n.tr("Unmark the selected items in the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:unmark", I18n.tr("Todo list: Unmark selected", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.model.unmarkItems(TodoDialog.this.model.getSelected());
      }

      @Override
      public void valueChanged(ListSelectionEvent var1) {
         this.updateEnabledState();
      }

      protected void updateEnabledState() {
         this.setEnabled(!TodoDialog.this.model.isSelectionEmpty());
      }
   }

   class UnmarkAllAction extends JosmAction {
      private static final long serialVersionUID = 1L;

      UnmarkAllAction() {
         super(
            I18n.tr("Unmark all", new Object[0]),
            "dialogs/refresh",
            I18n.tr("Unmark all items in the todo list.", new Object[0]),
            Shortcut.registerShortcut("subwindow:todo:unmark_all", I18n.tr("Todo list: Unmark all", new Object[0]), 65535, 5000),
            false
         );
      }

      public void actionPerformed(ActionEvent var1) {
         TodoDialog.this.model.unmarkAll();
      }
   }
}
