package consulo.apache.subversion;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.project.ui.notification.NotificationGroup;
import consulo.project.ui.notification.NotificationGroupContributor;
import jakarta.annotation.Nonnull;
import org.jetbrains.idea.svn.dialogs.CopiesPanel;

import java.util.function.Consumer;

/**
 * @author VISTALL
 * @since 01/06/2023
 */
@ExtensionImpl
public class SvnNotificationGroup implements NotificationGroupContributor {
  public static final NotificationGroup GROUP = NotificationGroup.balloonGroup("svn", LocalizeValue.localizeTODO("Subversion"));

  @Override
  public void contribute(@Nonnull Consumer<NotificationGroup> consumer) {
    consumer.accept(GROUP);
    consumer.accept(CopiesPanel.NOTIFICATION_GROUP);
  }
}
