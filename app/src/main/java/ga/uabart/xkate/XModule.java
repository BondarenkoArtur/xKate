package ga.uabart.xkate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static android.content.Context.MODE_PRIVATE;


public class XModule implements IXposedHookInitPackageResources, IXposedHookLoadPackage, IXposedHookZygoteInit {

    private Activity activity = null;
    private long chatId;
    private long groupId;
    private long messageUid;

    private String generateName(long messageUid, long chatId, long groupId) {
        if (messageUid != 0) {
            return "U" + messageUid;
        } else if (chatId != 0) {
            return "C" + chatId;
        } else if (groupId != 0) {
            return "G" + groupId;
        }
        return null;
    }

    private String getPass(String user, Activity activity) {
        if (user != null) {
            SharedPreferences prefs = activity.getSharedPreferences("XKate", MODE_PRIVATE);
            String pass = prefs.getString(user, null);
            if ("".equals(pass)) {
                pass = null;
            }
            return pass;
        } else {
            return null;
        }
    }

    private String getPassword(long messageUid, long chatId, long groupId, Activity activity) {
        return getPass(generateName(messageUid, chatId, groupId), activity);
    }

    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) throws Throwable {
        // maybe this will be needed in future
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable {

        if ("com.perm.kate".equals(param.packageName)) {

            try {

                final Class<?> messageThreadFragmentClass = XposedHelpers.findClass("com.perm.kate.MessageThreadFragment", param.classLoader);

                // Button

                XposedHelpers.findAndHookMethod(messageThreadFragmentClass, "onCreateView",
                        LayoutInflater.class, ViewGroup.class, Bundle.class, new XC_MethodHook() {

                            @Override
                            protected void afterHookedMethod(final MethodHookParam param) throws Throwable {
                                View sendButton = (View) XposedHelpers.getObjectField(param.thisObject, "sendButton");
                                activity = (Activity) XposedHelpers.callMethod(param.thisObject, "getActivity");
                                messageUid = XposedHelpers.getLongField(param.thisObject, "message_uid"); // long
                                chatId = XposedHelpers.getLongField(param.thisObject, "chat_id"); // long
                                groupId = XposedHelpers.getLongField(param.thisObject, "group_id"); // long
                                final String userName = generateName(messageUid, chatId, groupId);
                                sendButton.setOnLongClickListener(new View.OnLongClickListener() {
                                                                      @Override
                                                                      public boolean onLongClick(View v) {
                                                                          showDialogInputKey(userName, activity);
                                                                          return true;
                                                                      }
                                                                  }
                                );

                            }
                        });

                // Sender

                XposedHelpers.findAndHookMethod(messageThreadFragmentClass, "sendClicked", new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {

                        EditText etNewMessage = (EditText) XposedHelpers.getObjectField(param.thisObject, "et_new_message"); // EditText
                        Object attachments = XposedHelpers.getStaticObjectField(messageThreadFragmentClass, "attachments"); // ArrayList<String>
                        Object attachmentsObjects = XposedHelpers.getStaticObjectField(messageThreadFragmentClass, "attachments_objects"); // ArrayList<Object>
                        Object queue = XposedHelpers.getObjectField(param.thisObject, "queue"); // MessageSendQueue

                        String password;
                        password = getPassword(messageUid, chatId, groupId, activity);

                        if (password != null) {
                            String encryptedMsg = TextCipher.encrypt(etNewMessage.getText().toString(), password);
                            if (encryptedMsg != null) {
                                etNewMessage.setText(encryptedMsg);
                            }
                        }

                        XposedHelpers.callMethod(queue, "add", etNewMessage.getText().toString(), messageUid,
                                chatId, attachments, attachmentsObjects, null, groupId);

                        XposedHelpers.callMethod(param.thisObject, "clearNewMessageEditText");
                        XposedHelpers.callMethod(param.thisObject, "requeryOnUiThread");

                        return null;
                    }
                });

                // Receiver

                final Class<?> messageAdapterCoreClass = XposedHelpers.findClass("com.perm.kate.MessageAdapterCore", param.classLoader);
                final Class<?> userClass = XposedHelpers.findClass("com.perm.kate.api.User", param.classLoader);
                final Class<?> messageClass = XposedHelpers.findClass("com.perm.kate.api.Message", param.classLoader);
                final Class<?> userCacheClass = XposedHelpers.findClass("com.perm.utils.UserCache", param.classLoader);

                XposedHelpers.findAndHookMethod(messageAdapterCoreClass, "displayMessage",
                        userClass, messageClass, userClass, View.class, userCacheClass, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String body = (String) XposedHelpers.getObjectField(param.args[1], "body");
                                String password;
                                password = getPassword(messageUid, chatId, groupId, activity);
                                if (password != null) {
                                    String decrypted = TextCipher.decrypt(body, password);
                                    if (decrypted != null){
                                        body = decrypted;
                                    }
                                }
                                XposedHelpers.setObjectField(param.args[1], "body", body);
                            }
                        });

            } catch (Exception e) {
                XposedBridge.log(e);
            }


        }

    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        // maybe this will be needed in future
    }

    private void savePass(String user, String password, Activity activity) {
        SharedPreferences.Editor editor = activity.getSharedPreferences("XKate", MODE_PRIVATE).edit();
        editor.putString(user, password);
        editor.commit();

    }

    private void showDialogInputKey(final String userName, final Activity activity) {
        final EditText edittext = new EditText(activity);
        edittext.setText(getPass(userName, activity));

        AlertDialog alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setMessage("Input password");
        alertDialog.setView(edittext);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Save password",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                        String password = edittext.getText().toString();

                        savePass(userName, password, activity);
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

}
