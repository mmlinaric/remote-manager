package com.example.remotemanager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.remotemanager.model.AuthenticationType;
import com.example.remotemanager.ui.connections.ConnectionEditor;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ConnectionEditorLayoutTest {
  @Test
  void changingAuthenticationKeepsFieldsInsideDialogAndAtTop() throws Exception {
    Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    var previousLookAndFeel = UIManager.getLookAndFeel();
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    try {
      SwingUtilities.invokeAndWait(() -> {
        ConnectionEditor editor = new ConnectionEditor(null, null, null,
            List.of(), List.of(), List.of(), submission -> CompletableFuture.completedFuture(null));
        try {
          editor.setModalityType(Dialog.ModalityType.MODELESS);
          editor.setVisible(true);
          JScrollPane scroll = (JScrollPane) ((BorderLayout) editor.getContentPane().getLayout())
              .getLayoutComponent(BorderLayout.CENTER);
          JPanel form = (JPanel) scroll.getViewport().getView();
          @SuppressWarnings("unchecked")
          JComboBox<AuthenticationType> auth = (JComboBox<AuthenticationType>)
              fieldAt(form, "Authentication:", JComboBox.class);
          JComboBox<?> sudoSource = fieldAt(form, "Sudo password source:", JComboBox.class);
          JScrollPane notes = fieldAt(form, "Notes:", JScrollPane.class);
          JLabel name = java.util.Arrays.stream(form.getComponents())
              .filter(component -> component instanceof JLabel label
                  && label.getText().equals("Name:"))
              .map(component -> (JLabel) component).findFirst().orElseThrow();

          for (int sudo = 0; sudo < sudoSource.getItemCount(); sudo++) {
            sudoSource.setSelectedIndex(sudo);
            for (AuthenticationType type : AuthenticationType.values()) {
              auth.setSelectedItem(type);
              editor.validate();
              assertFalse(scroll.getHorizontalScrollBar().isVisible(),
                  type + " form=" + form.getPreferredSize().width
                      + " viewport=" + scroll.getViewport().getWidth());
              assertFalse(notes.getHorizontalScrollBar().isVisible(),
                  type + " / " + sudoSource.getSelectedItem() + " scrolled Notes sideways");
              assertTrue(notes.getHeight() >= 60,
                  type + " / " + sudoSource.getSelectedItem() + " collapsed Notes to "
                      + notes.getHeight() + " pixels (preferred " + notes.getPreferredSize().height
                      + ", form " + form.getHeight() + "/" + form.getPreferredSize().height
                      + ", viewport " + scroll.getViewport().getHeight() + ")");
              for (var component : form.getComponents()) {
                if (component.isVisible()) {
                  assertTrue(component.getX() + component.getWidth() <= form.getWidth(),
                      type + " pushed " + component.getClass().getSimpleName() + " outside the form");
                }
              }
              assertTrue(name.getY() <= 12, type + " placed Name too far from the top");
            }
          }
        } finally {
          editor.dispose();
        }
      });
    } finally {
      UIManager.setLookAndFeel(previousLookAndFeel);
    }
  }

  private static <T extends Component> T fieldAt(JPanel form, String labelText, Class<T> type) {
    GridBagLayout layout = (GridBagLayout) form.getLayout();
    JLabel label = java.util.Arrays.stream(form.getComponents())
        .filter(component -> component instanceof JLabel caption
            && caption.getText().equals(labelText))
        .map(component -> (JLabel) component).findFirst().orElseThrow();
    int row = layout.getConstraints(label).gridy;
    return java.util.Arrays.stream(form.getComponents())
        .filter(type::isInstance)
        .filter(component -> layout.getConstraints(component).gridy == row)
        .map(type::cast).findFirst().orElseThrow();
  }
}
