package org.openstreetmap.josm.plugins.todo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractModifiableLayer;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.util.TableHelper;
import org.openstreetmap.josm.tools.I18n;

public class TodoListModel extends AbstractListModel<TodoListItem> implements DataSetListener {
   private static final long serialVersionUID = 1L;
   private final List<TodoListItem> todoList = new ArrayList<>();
   private final Collection<TodoListItem> doneList = new HashSet<>();
   private final DefaultListSelectionModel selectionModel;

   public TodoListModel(DefaultListSelectionModel var1) {
      this.selectionModel = var1;
   }

   public TodoListItem getElementAt(int var1) {
      return this.todoList.get(var1);
   }

   @Override
   public int getSize() {
      return this.todoList.size();
   }

   public boolean isDone(TodoListItem var1) {
      return this.doneList.contains(var1);
   }

   public boolean isSelectionEmpty() {
      return this.selectionModel.isSelectionEmpty();
   }

   public int getDoneSize() {
      return this.doneList.size();
   }

   public List<TodoListItem> getTodoList() {
      return this.todoList;
   }

   public Collection<TodoListItem> getSelected() {
      return IntStream.range(0, this.todoList.size()).filter(this.selectionModel::isSelectedIndex).mapToObj(this.todoList::get).collect(Collectors.toSet());
   }

   public void setSelected(Collection<TodoListItem> var1) {
      int[] var2 = this.todoList.stream().filter(var1::contains).mapToInt(this.todoList::indexOf).toArray();
      Arrays.sort(var2);
      TableHelper.setSelectedIndices(this.selectionModel, IntStream.of(var2));
   }

   public Collection<TodoListItem> getItemsForPrimitives(Collection<? extends IPrimitive> var1) {
      HashMap var2 = new HashMap();

      for (IPrimitive var4 : var1) {
         var2.put(var4.getPrimitiveId(), var4);
      }

      return this.todoList.stream().filter(var1x -> var2.containsKey(var1x.primitive.getPrimitiveId())).collect(Collectors.toList());
   }

   public void addItems(Collection<? extends IPrimitive> var1) {
      if (var1 != null && !var1.isEmpty()) {
         LinkedHashMap<AbstractOsmDataLayer, LinkedHashSet<IPrimitive>> var2 = new LinkedHashMap<>();

         for (Layer var4 : MainApplication.getLayerManager().getLayers()) {
            if (var4 instanceof AbstractOsmDataLayer var5) {
               LinkedHashSet<IPrimitive> var6 = new LinkedHashSet<>();

               for (IPrimitive var8 : var1) {
                  if (var8.getDataSet() != null && var8.getDataSet().equals(var5.getDataSet())) {
                     var6.add(var8);
                  }
               }

               if (!var6.isEmpty()) {
                  var2.put(var5, var6);
               }
            }
         }

         if (!var2.isEmpty()) {
            ArrayList<TodoListItem> var9 = new ArrayList<>();

            for (Entry<AbstractOsmDataLayer, LinkedHashSet<IPrimitive>> var12 : var2.entrySet()) {
               for (IPrimitive var14 : var12.getValue()) {
                  TodoListItem var15 = new TodoListItem(var14, (AbstractOsmDataLayer)var12.getKey());
                  if (!this.todoList.contains(var15) && !this.doneList.contains(var15)) {
                     var9.add(var15);
                  }
               }
            }

            if (!var9.isEmpty()) {
               int var11 = this.todoList.size();
               this.todoList.addAll(var9);
               this.fireIntervalAdded(this, var11, this.todoList.size() - 1);
               if (this.selectionModel.isSelectionEmpty()) {
                  this.selectionModel.setSelectionInterval(var11, var11);
               }
            }
         }
      }
   }

   public void incrementSelection() {
      if (!this.todoList.isEmpty()) {
         int var1 = this.selectionModel.getMinSelectionIndex();
         if (var1 < 0) {
            var1 = 0;
         }

         int var2 = (var1 + 1) % this.todoList.size();
         this.selectionModel.setSelectionInterval(var2, var2);
      }
   }

   public void markItems(Collection<TodoListItem> var1) {
      if (var1 != null && !var1.isEmpty()) {
         HashMap<TodoListItem, Integer> var2 = new HashMap<>();

         for (int var3 = 0; var3 < this.todoList.size(); var3++) {
            var2.put(this.todoList.get(var3), var3);
         }

         this.doneList.addAll(var1);
         int[] var4 = var1.stream().filter(var2::containsKey).mapToInt(var2::get).sorted().toArray();
         if (var4.length > 0) {
            this.fireContentsChanged(this, var4[0], var4[var4.length - 1]);
         }

         this.incrementSelection();
      }
   }

   public void markAll() {
      if (!this.todoList.isEmpty()) {
         this.doneList.addAll(this.todoList);
         this.fireContentsChanged(this, 0, this.todoList.size() - 1);
      }
   }

   public void unmarkAll() {
      if (!this.doneList.isEmpty()) {
         this.doneList.clear();
         if (!this.todoList.isEmpty()) {
            this.fireContentsChanged(this, 0, this.todoList.size() - 1);
         }
      }
   }

   public void unmarkItems(Collection<TodoListItem> var1) {
      if (var1 != null && !var1.isEmpty()) {
         boolean var2 = this.doneList.removeAll(var1);
         if (var2 && !this.todoList.isEmpty()) {
            this.fireContentsChanged(this, 0, this.todoList.size() - 1);
         }
      }
   }

   public void clearAll() {
      if (!this.todoList.isEmpty() || !this.doneList.isEmpty()) {
         int var1 = this.todoList.size();
         this.todoList.clear();
         this.doneList.clear();
         this.selectionModel.clearSelection();
         if (var1 > 0) {
            this.fireIntervalRemoved(this, 0, var1 - 1);
         }
      }
   }

   public boolean purgeLayerItems(AbstractModifiableLayer var1) {
      boolean var2 = this.todoList.removeIf(var1x -> var1x.layer.equals(var1));
      this.doneList.removeIf(var1x -> var1x.layer.equals(var1));
      if (var2) {
         this.fireIntervalRemoved(this, 0, Math.max(0, this.todoList.size()));
      }

      return var2;
   }

   public String getSummary() {
      int var1 = this.todoList.size();
      int var2 = this.doneList.size();
      if (var1 == 0) {
         return I18n.tr("Todo list", new Object[0]);
      } else {
         int var3 = (int)Math.round(100.0 * var2 / var1);
         return I18n.tr("Todo list {0}/{1} ({2}%)", new Object[]{var2, var1, var3});
      }
   }

   public void primitivesAdded(PrimitivesAddedEvent var1) {
   }

   public void primitivesRemoved(PrimitivesRemovedEvent var1) {
   }

   public void tagsChanged(TagsChangedEvent var1) {
   }

   public void nodeMoved(NodeMovedEvent var1) {
   }

   public void wayNodesChanged(WayNodesChangedEvent var1) {
   }

   public void relationMembersChanged(RelationMembersChangedEvent var1) {
   }

   public void otherDatasetChange(AbstractDatasetChangedEvent var1) {
   }

   public void dataChanged(DataChangedEvent var1) {
   }
}
