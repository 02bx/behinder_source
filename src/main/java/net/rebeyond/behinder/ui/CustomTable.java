package net.rebeyond.behinder.ui;

import net.rebeyond.behinder.utils.Constants;
import org.eclipse.swt.widgets.*;

public class CustomTable extends Table {
    public CustomTable(Composite arg0, int arg1, int menuStyle) {
        super(arg0, arg1);
        addContextMenu(this, menuStyle);
    }

    public static void addContextMenu(final Table control, int menuStyle) {
        Menu menu = new Menu(control);
        if ((Constants.MENU_CUT & menuStyle) > 0) {
            MenuItem item = new MenuItem(menu, 8);
            item.setText("����");
            item.addListener(13, new Listener() {
                public void handleEvent(Event event) {
                }
            });
        }
        if ((Constants.MENU_COPY & menuStyle) > 0) {
            MenuItem item2 = new MenuItem(menu, 8);
            item2.setText("����");
            item2.addListener(13, new Listener() {
                public void handleEvent(Event event) {
                }
            });
        }
        if ((Constants.MENU_PASTE & menuStyle) > 0) {
            MenuItem item3 = new MenuItem(menu, 8);
            item3.setText("ճ��");
            item3.addListener(13, new Listener() {
                public void handleEvent(Event event) {
                }
            });
        }
        if ((Constants.MENU_SELECT_ALL & menuStyle) > 0) {
            MenuItem item4 = new MenuItem(menu, 8);
            item4.setText("ȫѡ");
            item4.addListener(13, new Listener() {
                public void handleEvent(Event event) {
                    control.selectAll();
                }
            });
        }
        if ((Constants.MENU_CLEAR & menuStyle) > 0) {
            MenuItem item5 = new MenuItem(menu, 8);
            item5.setText("���");
            item5.addListener(13, new Listener() {
                public void handleEvent(Event event) {
                }
            });
        }
        control.setMenu(menu);
    }
}
