package org.basex.gui.layout;

import static org.basex.gui.layout.BaseXKeys.*;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.text.*;

import org.basex.gui.*;
import org.basex.gui.listener.*;
import org.basex.util.*;
import org.basex.util.options.*;

/**
 * Project specific ComboBox implementation.
 *
 * @author BaseX Team 2005-17, BSD License
 * @author Christian Gruen
 */
public class BaseXCombo extends JComboBox<Object> {
  /** Options. */
  private Options options;
  /** Option. */
  private Option<?> option;
  /** History. */
  private BaseXHistory history;
  /** History listener. */
  private KeyListener listener;
  /** Hint. */
  private BaseXTextHint hint;

  /** Reference to parent window (of type {@link BaseXDialog} or {@link GUI}). */
  private final BaseXWindow win;

  /**
   * Constructor.
   * @param win parent window
   * @param option numeric value
   * @param options options
   * @param values combobox values
   */
  public BaseXCombo(final BaseXWindow win, final NumberOption option, final Options options,
      final String... values) {
    this(win, option, options, false, values);
    setSelectedItem(values[options.get(option)]);
  }

  /**
   * Constructor.
   * @param win parent window
   * @param option boolean value
   * @param options options
   */
  public BaseXCombo(final BaseXWindow win, final BooleanOption option, final Options options) {
    this(win, option, options, false, "true", "false");
    setSelectedItem(options.get(option));
  }

  /**
   * Constructor.
   * @param win parent window
   * @param option enum value
   * @param options options
   */
  public BaseXCombo(final BaseXWindow win, final EnumOption<?> option, final Options options) {
    this(win, option, options, false, option.strings());
    setSelectedItem(options.get(option));
  }

  /**
   * Attaches a history and enables a listener for cursor down/up keys.
   * @param opt strings option
   * @param opts options
   * @return self reference
   */
  public final BaseXCombo history(final StringsOption opt, final Options opts) {
    if(!isEditable()) throw Util.notExpected("Combobox is not editable.");

    options = opts;
    option = opt;
    history = new BaseXHistory(opt, options);
    setItems(opts.get(opt));

    final JTextComponent comp = textField();
    comp.removeKeyListener(listener);
    listener = (KeyPressedListener) e -> {
      if(ENTER.is(e)) {
        store();
      } else if(NEXTLINE.is(e) || PREVLINE.is(e)) {
        if(e.isShiftDown()) {
          final String value = history.get(NEXTLINE.is(e));
          if(value != null) {
            setText(value);
            final BaseXDialog dialog = win.dialog();
            if(dialog != null) dialog.action(this);
          }
        }
      }
    };
    comp.addKeyListener(listener);
    return this;
  }

  /**
   * Constructor.
   * @param win parent window
   * @param option option
   * @param options options
   * @param values values
   * @param editable editable flag
   */
  private BaseXCombo(final BaseXWindow win, final Option<?> option, final Options options,
      final boolean editable, final String... values) {
    this(win, editable, values);
    this.options = options;
    this.option = option;
  }

  /**
   * Constructor.
   * @param win parent window
   * @param values combobox values
   */
  public BaseXCombo(final BaseXWindow win, final String... values) {
    this(win, false, values);
  }

  /**
   * Constructor.
   * @param win parent window
   * @param editable editable flag
   * @param values combobox values
   */
  public BaseXCombo(final BaseXWindow win, final boolean editable, final String... values) {
    super(values);
    this.win = win;

    setEditable(editable);
    setEditor(new BaseXEditor(win.gui()));

    BaseXLayout.addInteraction(this, win);

    final BaseXDialog dialog = win.dialog();
    if(dialog == null) return;

    SwingUtilities.invokeLater(() -> {
      final BaseXTextField tf = textField();
      if(tf == null) {
        addActionListener(e -> dialog.action(BaseXCombo.this));
      } else {
        tf.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void removeUpdate(final DocumentEvent e) { dialog.action(BaseXCombo.this); }
          @Override
          public void insertUpdate(final DocumentEvent e) { dialog.action(BaseXCombo.this); }
          @Override
          public void changedUpdate(final DocumentEvent e) { }
        });
      }
    });
  }

  /**
   * Adds a hint to the text field.
   * @param label text of the hint
   * @return self reference
   */
  public BaseXCombo hint(final String label) {
    final BaseXTextField tf = textField();
    if(tf != null) {
      if(hint == null) {
        hint = new BaseXTextHint(label, tf);
      } else {
        hint.setText(label);
      }
    }
    setToolTipText(label.replaceAll("\\.\\.\\.$", ""));
    return this;
  }

  /**
   * Sets the specified items.
   * @param items items
   */
  public void setItems(final String... items) {
    setModel(new DefaultComboBoxModel<>(items));
  }

  /**
   * Stores the current history and refreshes the selectable items.
   */
  public void store() {
    if(history != null) {
      history.store(getText());
      SwingUtilities.invokeLater(() -> setItems(history.values()));
    }
  }

  /**
   * Returns the current text.
   * @return text
   */
  public String getText() {
    return getSelectedItem();
  }

  /**
   * Sets the current text.
   * @param text text to be assigned
   */
  public void setText(final String text) {
    setSelectedItem(text);
  }

  @Override
  public String getSelectedItem() {
    final Object item = isEditable() ? getEditor().getItem() : super.getSelectedItem();
    return item == null ? "" : item.toString();
  }

  /**
   * Returns the editor text field, or {@code null} if the combobox is not editable.
   * @return text field
   */
  public BaseXTextField textField() {
    return isEditable() ? (BaseXTextField) getEditor().getEditorComponent() : null;
  }

  @Override
  public void setSelectedItem(final Object object) {
    if(object == null) return;

    if(isEditable()) {
      getEditor().setItem(object);
    } else {
      final ComboBoxModel<Object> model = getModel();
      final int ms = model.getSize();
      for(int m = 0; m < ms; m++) {
        if(model.getElementAt(m).equals(object)) {
          super.setSelectedItem(object);
          return;
        }
      }
    }
    if(hint != null) hint.update();
  }

  /**
   * Highlights the text field.
   * @param hits  hits
   */
  public synchronized void highlight(final boolean hits) {
    final BaseXTextField tf = textField();
    (tf != null ? tf : this).setBackground(hits ? GUIConstants.BACK : GUIConstants.LRED);
  }

  @Override
  public synchronized KeyListener[] getKeyListeners() {
    final BaseXTextField tf = textField();
    return tf != null ? tf.getKeyListeners() : super.getKeyListeners();
  }

  @Override
  public synchronized void addKeyListener(final KeyListener l) {
    final BaseXTextField tf = textField();
    if(tf != null) tf.addKeyListener(l);
    else super.addKeyListener(l);
  }

  @Override
  public synchronized void removeKeyListener(final KeyListener l) {
    final BaseXTextField tf = textField();
    if(tf != null) tf.removeKeyListener(l);
    super.removeKeyListener(l);
  }

  @Override
  public synchronized void addFocusListener(final FocusListener l) {
    final BaseXTextField tf = textField();
    if(tf != null) tf.addFocusListener(l);
    else super.addFocusListener(l);
  }

  @Override
  public synchronized void removeFocusListener(final FocusListener l) {
    final BaseXTextField tf = textField();
    if(tf != null) tf.removeFocusListener(l);
    super.removeFocusListener(l);
  }

  /**
   * Assigns the current checkbox value to the option specified in the constructor.
   */
  public void assign() {
    if(option instanceof NumberOption) {
      options.set((NumberOption) option, getSelectedIndex());
    } else if(option instanceof EnumOption) {
      options.set((EnumOption<?>) option, getSelectedItem());
    } else if(option instanceof StringOption) {
      options.set((StringOption) option, getSelectedItem());
    } else if(option instanceof BooleanOption) {
      options.set((BooleanOption) option, Boolean.parseBoolean(getSelectedItem()));
    } else if(option instanceof StringsOption) {
      store();
    } else {
      throw Util.notExpected("Option type not supported: " + option);
    }
  }

  /**
   * Combo box editor.
   *
   * @author BaseX Team 2005-17, BSD License
   * @author Christian Gruen
   */
  private static final class BaseXEditor implements ComboBoxEditor {
    /** Text field. */
    private final BaseXTextField tf;

    /**
     * Constructor.
     * @param gui gui
     */
    private BaseXEditor(final GUI gui) {
      tf = new BaseXTextField(gui);
      tf.setBorder(new EmptyBorder(0, 4, 0, 0));
    }
    @Override
    public Component getEditorComponent() {
      return tf;
    }
    @Override
    public void setItem(final Object text) {
      if(text != null) tf.setText(text.toString());
    }
    @Override
    public String getItem() {
      return tf.getText();
    }
    @Override
    public void selectAll() {
      tf.selectAll();
    }
    @Override
    public void addActionListener(final ActionListener l) {
      tf.addActionListener(l);
    }
    @Override
    public void removeActionListener(final ActionListener l) {
      tf.removeActionListener(l);
    }
  }
}
