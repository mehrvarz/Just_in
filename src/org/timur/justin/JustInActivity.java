/*
 * Copyright (C) 2011 Timur Mehrvarz Duesseldorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.timur.justin;

import android.content.Context;
import android.content.Intent;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.util.Config;
import android.util.Log;
import android.os.Bundle;
import android.app.Dialog;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputFilter;

import org.timur.glticker.TickerServiceAbstract;
import org.timur.glticker.GlTickerView;
import com.vodafone.twitter.service.TwitterServiceAbstract;
import twitter4j.*;

public class JustInActivity extends org.timur.glticker.GlActivityAbstract {

  @Override
  protected void setAppConfig() {
    appname = "Just in...";
    LOGTAG = "JustInActivity";
    glViewClassName = "org.timur.glticker.GlTicker2View";
    autoForwardDelay = 4000l;
    serviceClassName = "com.vodafone.twitter.service.TwitterService";
    serviceArg1 = Constants.CONSUMERKEY;
    serviceArg2 = Constants.CONSUMERSECRET;
    TICKER_NEW_ENTRY_BROADCAST_ACTION = "org.timur.JustInBroadcast";
    MAX_NUMBER_ONLOAD_MSGS = org.timur.glticker.GlTickerView.MAX_BITMAPS-3;
    PREFS_NAME = "org.timur.justin";
  }

  ///////////////////////////////////////////////////////////////// options menu

  public static final int MENU_HELP = 10;
  public static final int MENU_NEW_TWEET = 11;

  @Override 
  public boolean onCreateOptionsMenu(Menu menu) {
    menu.add(Menu.NONE, MENU_NEW_TWEET, Menu.NONE, "New Tweet");
    menu.add(Menu.NONE, MENU_HELP, Menu.NONE, "Help");
    return super.onCreateOptionsMenu(menu);
  }

  @Override 
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case MENU_NEW_TWEET:
        if(serviceClientObject!=null) {
          TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
          TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
          if(twitterService!=null) {
            final Twitter twitter = twitterService.getTwitterObject();
            if(twitter!=null) {
              final AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
              final EditText editText = new EditText(context);
              int maxLength = 140;
              InputFilter[] FilterArray = new InputFilter[1];
              FilterArray[0] = new InputFilter.LengthFilter(maxLength);
              editText.setFilters(FilterArray);
              DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  switch(which) {
                    case DialogInterface.BUTTON_POSITIVE:
                      String inputText = editText.getText().toString();
                      try {
                        twitter.updateStatus(inputText);
                      } catch(twitter4j.TwitterException twex) {
                        Log.e(LOGTAG, "FAILED on twitter.updateStatus() "+twex);
                        Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                      }
                      break;
                    case DialogInterface.BUTTON_NEGATIVE:
                      break;
                  }
                }
              };
              alertDialogBuilder.setTitle("New tweet");
              alertDialogBuilder.setView(editText);
              alertDialogBuilder.setPositiveButton("Send",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
            }
          }
        }
        return true;

      case MENU_HELP:
        GlTickerView.openInBrowser("http://timur.mobi/justin-app/");
        return true;
    }
    return super.onOptionsItemSelected(item);
  }


  ///////////////////////////////////////////////////////////////// popup menu

  public static final int DIALOG_PROMOTE = 10;

  @Override
  public Dialog onCreateDialog(final int id) {
    Dialog menuDialog = null;

    if(id==DIALOG_ABOUT) {
      if(Config.LOGD) Log.i(LOGTAG, "onCreateDialog id==DIALOG_ABOUT setContentView(R.layout.about_dialog)");
      menuDialog = new Dialog(this,R.style.NoTitleDialog);
      menuDialog.setContentView(R.layout.about_dialog);

      PackageInfo pinfo;
      int versionNumber = 0;
      String versionName = "";
      try {
        pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
        versionNumber = pinfo.versionCode;
        versionName = pinfo.versionName;
        if(Config.LOGD) Log.i(LOGTAG, "onCreateDialog id==DIALOG_ABOUT manifest versionName="+versionName);
        TextView textView = (TextView)menuDialog.findViewById(R.id.aboutVersion);
        textView.setText((CharSequence)("v"+versionName),TextView.BufferType.NORMAL);
      } catch(android.content.pm.PackageManager.NameNotFoundException nnfex) {
        Log.e(LOGTAG, "onClick btnAbout FAILED on getPackageManager().getPackageInfo(getPackageName(), 0) "+nnfex);
      }

      return menuDialog;
    } 


    final GradientDrawable mBackgroundGradient =
          new GradientDrawable(
                  GradientDrawable.Orientation.TOP_BOTTOM,
                  new int[]{0x30606060,0x30000000});

    if(currentEntryTopic!=null) {
      if(Config.LOGD) Log.i(LOGTAG, "onCreateDialog() id="+id+" link="+currentEntryTopic.link);
    }
    menuDialog = new Dialog(this,R.style.NoTitleDialog);

    if(id==DIALOG_USE_MSG) {
      menuDialog.setContentView(R.layout.open_dialog);
      menuDialog.findViewById(R.id.buttonLayout).setBackgroundDrawable(mBackgroundGradient);

      // tweet
      Button btnTweetReply = (Button)menuDialog.findViewById(R.id.buttonOpenTweetReply);
      if(btnTweetReply!=null) {
        btnTweetReply.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnTweetReply");
            if(currentEntryTopic!=null) {
              if(serviceClientObject!=null) {
                TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
                TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
                if(twitterService!=null) {
                  final Twitter twitter = twitterService.getTwitterObject();
                  if(twitter!=null) {
                    final EditText editText = new EditText(context);
                    int maxLength = 140;
                    InputFilter[] FilterArray = new InputFilter[1];
                    FilterArray[0] = new InputFilter.LengthFilter(maxLength);
                    editText.setFilters(FilterArray);
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                        switch(which) {
                          case DialogInterface.BUTTON_POSITIVE:
                            String inputText = editText.getText().toString();
                            try {
                              twitter.updateStatus(inputText);
                            } catch(twitter4j.TwitterException twex) {
                              Log.e(LOGTAG, "FAILED on twitter.updateStatus() "+twex);
                              Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                            break;
                          case DialogInterface.BUTTON_NEGATIVE:
                            break;
                        }
                      }
                    };
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    alertDialogBuilder.setTitle("Reply tweet");
                    alertDialogBuilder.setMessage(ripTags(currentEntryTopic.title));
                    editText.setText("@"+currentEntryTopic.shortName+" "+ripTags(currentEntryTopic.title),TextView.BufferType.EDITABLE);
                    // todo: should show length of message (or rather: 140 - number of characters)
                    alertDialogBuilder.setView(editText);
                    alertDialogBuilder.setPositiveButton("Send",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
                  }
                }
              }
            }
            dismissDialog(id);
          } 
        });
      }

      // retweet
      Button btnRetweet = (Button)menuDialog.findViewById(R.id.buttonOpenRetweet);
      if(btnRetweet!=null) {
        btnRetweet.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnRetweet");
            if(currentEntryTopic!=null) {
              if(serviceClientObject!=null) {
                TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
                TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
                if(twitterService!=null) {
                  final Twitter twitter = twitterService.getTwitterObject();
                  if(twitter!=null) {
                    // yes/no dialog
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                          case DialogInterface.BUTTON_POSITIVE:
                            try {
                              twitter.retweetStatus(currentEntryTopic.id);
                              Toast.makeText(context, "delivered retweet", Toast.LENGTH_SHORT).show();
                            } catch(twitter4j.TwitterException twex) {
                              Log.e(LOGTAG, "FAILED on twitter.retweetStatus() "+twex);
                              Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                            break;
                          case DialogInterface.BUTTON_NEGATIVE:
                            break;
                        }
                      }
                    };
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    alertDialogBuilder.setTitle("Retweet");
                    alertDialogBuilder.setMessage(ripTags(currentEntryTopic.title));
                    alertDialogBuilder.setPositiveButton("Send",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
                  }
                }
              }
            }
            dismissDialog(id);
          } 
        });
      }

      // mark as Favorit
      Button btnMarkFavorit = (Button)menuDialog.findViewById(R.id.buttonMarkFavorit);
      if(btnMarkFavorit!=null) {
        btnMarkFavorit.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit");
            if(currentEntryTopic!=null) {
              if(serviceClientObject!=null) {
                if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit get tickerService...");
                TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
                TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
                if(twitterService!=null) {
                  if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit got twitterService");
                  final Twitter twitter = twitterService.getTwitterObject();
                  if(twitter!=null) {
                    if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit got twitterObject");
                    // yes/no dialog
                    DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                      @Override
                      public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                          case DialogInterface.BUTTON_POSITIVE:
                            try {
                              twitter.createFavorite(currentEntryTopic.id);
                            } catch(twitter4j.TwitterException twex) {
                              Log.e(LOGTAG, "FAILED on twitter.retweetStatus() "+twex);
                              Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                            }
                            break;
                          case DialogInterface.BUTTON_NEGATIVE:
                            break;
                        }
                      }
                    };
                    AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                    if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit got alertDialogBuilder");
                    alertDialogBuilder.setTitle("Mark as favorit");
                    alertDialogBuilder.setMessage(ripTags(currentEntryTopic.title));
                    if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit alertDialogBuilder start...");
                    alertDialogBuilder.setPositiveButton("Favorit",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
                    if(Config.LOGD) Log.i(LOGTAG, "onClick btnMarkFavorit alertDialogBuilder started");
                  }
                }
              }
            }
            dismissDialog(id);
          } 
        });
      }

      // Browse...
      Button btnBrowse = (Button)menuDialog.findViewById(R.id.buttonOpenBrowse);
      if(btnBrowse!=null) {
        btnBrowse.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "btnBrowse currentEntryTopic="+currentEntryTopic);
            if(currentEntryTopic!=null) {
              glView.openCurrentMsgInBrowser(currentEntryTopic);
            }
            dismissDialog(id);
          } 
        });
      }

      // Send by Mail
      Button btnEmail = (Button)menuDialog.findViewById(R.id.buttonOpenEmail);
      if(btnEmail!=null) {
        btnEmail.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "btnEmail currentEntryTopic="+currentEntryTopic);
            if(currentEntryTopic!=null && currentEntryTopic.title!=null) {
              Intent sendMailIntent = new Intent(Intent.ACTION_SEND);
              sendMailIntent.putExtra(Intent.EXTRA_SUBJECT, currentEntryTopic.channelName + ": " + ripTags(currentEntryTopic.title).substring(0,40)+"...");
              sendMailIntent.putExtra(Intent.EXTRA_TEXT, ripTags(currentEntryTopic.title));
              sendMailIntent.setType("message/rfc822");                       
              startActivity(sendMailIntent);
            }
            dismissDialog(id);
          } 
        });
      }

      // Send by SMS
      Button btnSMS = (Button)menuDialog.findViewById(R.id.buttonOpenSMS);
      if(btnSMS!=null) {
        btnSMS.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "btnSMS currentEntryTopic="+currentEntryTopic);
            if(currentEntryTopic!=null) {
              Uri smsUri = Uri.parse("smsto:");
              Intent sendSmsIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
              sendSmsIntent.putExtra("sms_body", currentEntryTopic.channelName + ": " + ripTags(currentEntryTopic.title));
              //sendSmsIntent.setType("vnd.android-dir/mms-sms");
              try {
                startActivity(sendSmsIntent);
              } catch(Exception ex) {
                String errMsg = ex.getMessage();
                Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show();
              }
            }
            dismissDialog(id);
          } 
        });
      }

//      // bluetooth
//      Button btnBluetooth = (Button)menuDialog.findViewById(R.id.buttonOpenBluetooth);
//      if(btnBluetooth!=null) {
//        btnBluetooth.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View view) { 
//              if(currentEntryTopic!=null) {
//                // todo: must implement
//                Toast.makeText(context, "bluetooth not yet implemented", Toast.LENGTH_LONG).show();
//              }
//              dismissDialog(id);
//            } 
//          }
//        );
//      }

      // close
      Button btnClose = (Button)menuDialog.findViewById(R.id.buttonClose);
      if(btnClose!=null) {
        btnClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { 
              dismissDialog(id);
            } 
          }
        );
      }

    } 
    else
    if(id==DIALOG_MORE) {
      menuDialog.setContentView(R.layout.more_dialog);
      menuDialog.findViewById(R.id.buttonLayout).setBackgroundDrawable(mBackgroundGradient);

      // browse Favorits
      Button btnBrowseFavorits = (Button)menuDialog.findViewById(R.id.buttonBrowseFavorits);
      if(btnBrowseFavorits!=null) {
        btnBrowseFavorits.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnBrowseFavorits glView="+glView);
            if(glView!=null) {
              if(serviceClientObject!=null) {
                TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
                TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
                if(Config.LOGD) Log.i(LOGTAG, "onClick btnBrowseFavorits twitterService="+twitterService);
                if(twitterService!=null) {
                  final Twitter twitter = twitterService.getTwitterObject();
                  if(twitter!=null) {
                    try {
                      glView.openInBrowser("http://twitter.com/"+twitter.getScreenName()+"/favorites");
                    } catch(twitter4j.TwitterException twex) {
                      Log.e(LOGTAG, "FAILED on twitter.getScreenName() "+twex);
                      Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                    }
                  }
                }
              }
            }
            dismissDialog(id);
          } 
        });
      }

      // re-login / clear password
      Button btnClearPassword = (Button)menuDialog.findViewById(R.id.buttonClearPassword);
      if(btnClearPassword!=null) {
        btnClearPassword.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword serviceClientObject="+serviceClientObject);
            if(serviceClientObject!=null) {
              final TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
              final TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
              if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword twitterService="+twitterService);
              if(twitterService!=null) {

                // yes/no dialog
                DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    switch (which){
                      case DialogInterface.BUTTON_POSITIVE:
                        if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword MENU_CLEAR_PASSWORD");
                        twitterService.clearTwitterLogin();
                        // restart activity
                        if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword restart activity");
                        Intent intent = getIntent();
                        overridePendingTransition(0, 0);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                        finish();
                        try { Thread.sleep(1500); } catch(Exception ex2) {};
                        if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword finish() done, starting...");
                        overridePendingTransition(0, 0);
                        startActivity(intent);            
                        // on restart, OAuthActivity will be opened
                        break;
                      case DialogInterface.BUTTON_NEGATIVE:
                        break;
                    }
                  }
                };
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword got alertDialogBuilder");
                alertDialogBuilder.setTitle("Re-Login");
                if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword alertDialogBuilder start...");
                alertDialogBuilder.setPositiveButton("Re-Login",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
                if(Config.LOGD) Log.i(LOGTAG, "onClick btnClearPassword alertDialogBuilder started");
              }
            }

            dismissDialog(id);
          } 
        });
      }

      // shutdown all
      Button btnShutdownAll = (Button)menuDialog.findViewById(R.id.buttonShutdownAll);
      if(btnShutdownAll!=null) {
        btnShutdownAll.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnShutdownAll serviceClientObject="+serviceClientObject);

            DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                switch(which) {
                  case DialogInterface.BUTTON_POSITIVE:
                    if(serviceClientObject!=null) {
                      TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
                      if(tickerService!=null) {
                        if(Config.LOGD) Log.i(LOGTAG, "onClick btnShutdownAll tickerService.stopSelf()");
                        tickerService.stopSelf();
                      }
                    }
                    if(Config.LOGD) Log.i(LOGTAG, "onClick btnShutdownAll System.exit(0)");
                    System.exit(0);
                    break;
                  case DialogInterface.BUTTON_NEGATIVE:
                    break;
                }
              }
            };
            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
            alertDialogBuilder.setTitle("Shutdown all");
            alertDialogBuilder.setPositiveButton("Shut down",dialogClickListener).setNegativeButton("Keep running", dialogClickListener).show();
            dismissDialog(id);
          } 
        });
      }

      // about
      Button btnAbout = (Button)menuDialog.findViewById(R.id.buttonAbout);
      if(btnAbout!=null) {
        btnAbout.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnAbout");
            showDialog(DIALOG_ABOUT);
            dismissDialog(id);
          } 
        });
      }

      // promote
      Button btnPromote = (Button)menuDialog.findViewById(R.id.buttonPromote);
      if(btnPromote!=null) {
        btnPromote.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnPromote");
            showDialog(DIALOG_PROMOTE);
            dismissDialog(id);
          } 
        });
      }

      // close
      Button btnClose = (Button)menuDialog.findViewById(R.id.buttonClose);
      if(btnClose!=null) {
        btnClose.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) { 
              dismissDialog(id);
            } 
          }
        );
      }
    }
    else
    if(id==DIALOG_PROMOTE) {
      if(Config.LOGD) Log.i(LOGTAG, "onCreateDialog id==DIALOG_PROMOTE setContentView(R.layout.promote_dialog)");
      menuDialog = new Dialog(this,R.style.NoTitleDialog);
      menuDialog.setContentView(R.layout.promote_dialog);

      // promote mail
      Button btnPromoteMail = (Button)menuDialog.findViewById(R.id.buttonPromoteMail);
      if(btnPromoteMail!=null) {
        btnPromoteMail.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnPromoteMail");

            Intent sendMailIntent = new Intent(Intent.ACTION_SEND);
            sendMailIntent.putExtra(Intent.EXTRA_SUBJECT, "Just in... for Android");
            sendMailIntent.putExtra(Intent.EXTRA_TEXT, 
              "Hi \ncheck this out...\n\nJust in... OpenGL Twitter reader free download from Android Market\nhttp://market.android.com/details?id=org.timur.justin\n");
            sendMailIntent.setType("message/rfc822");                       
            startActivity(sendMailIntent);

            dismissDialog(id);
          } 
        });
      }

      // promote SMS
      Button btnPromoteSMS = (Button)menuDialog.findViewById(R.id.buttonPromoteSMS);
      if(btnPromoteSMS!=null) {
        btnPromoteSMS.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnPromoteSMS");

            Uri smsUri = Uri.parse("smsto:");
            Intent sendSmsIntent = new Intent(Intent.ACTION_SENDTO, smsUri);
            sendSmsIntent.putExtra("sms_body", 
              "Just in... OpenGL Twitter reader free DL from Android Market http://market.android.com/details?id=org.timur.justin");
            //sendSmsIntent.setType("vnd.android-dir/mms-sms");
            try {
              startActivity(sendSmsIntent);
            } catch(Exception ex) {
              String errMsg = ex.getMessage();
              Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show();
            }

            dismissDialog(id);
          } 
        });
      }

      // promote tweet
      Button btnPromoteTweet = (Button)menuDialog.findViewById(R.id.buttonPromoteTweet);
      if(btnPromoteTweet!=null) {
        btnPromoteTweet.setOnClickListener(new View.OnClickListener() {
          public void onClick(View view) { 
            if(Config.LOGD) Log.i(LOGTAG, "onClick btnPromoteTweet");

            if(serviceClientObject!=null) {
              TickerServiceAbstract tickerService = serviceClientObject.getServiceObject();
              TwitterServiceAbstract twitterService = (TwitterServiceAbstract)tickerService;
              if(twitterService!=null) {
                final Twitter twitter = twitterService.getTwitterObject();
                if(twitter!=null) {
                  final EditText editText = new EditText(context);
                  int maxLength = 140;
                  InputFilter[] FilterArray = new InputFilter[1];
                  FilterArray[0] = new InputFilter.LengthFilter(maxLength);
                  editText.setFilters(FilterArray);
                  DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                      switch(which) {
                        case DialogInterface.BUTTON_POSITIVE:
                          String inputText = editText.getText().toString();
                          try {
                            twitter.updateStatus(inputText);
                          } catch(twitter4j.TwitterException twex) {
                            Log.e(LOGTAG, "FAILED on twitter.updateStatus() "+twex);
                            Toast.makeText(context, twex.getMessage(), Toast.LENGTH_LONG).show();
                          }
                          break;
                        case DialogInterface.BUTTON_NEGATIVE:
                          break;
                      }
                    }
                  };
                  AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                  alertDialogBuilder.setTitle("Tweet");
                  //alertDialogBuilder.setMessage("");
                  editText.setText("'Just in...' #OpenGL #Twitter reader free DL from #Android Market http://market.android.com/details?id=org.timur.justin",TextView.BufferType.EDITABLE);
                  // todo: should show length of message (or rather: 140 - number of characters)
                  alertDialogBuilder.setView(editText);
                  alertDialogBuilder.setPositiveButton("Send",dialogClickListener).setNegativeButton("Abort", dialogClickListener).show();
                }
              }
            }

            dismissDialog(id);
          } 
        });
      }
    }

    return menuDialog;
  }
  
  private String ripTags(String text) {
    if(Config.LOGD) Log.i(LOGTAG, "ripTags ["+text+"]");
    int idxTagOpen = text.indexOf("<");
    while(idxTagOpen>=0) {
      String tagOpen = text.substring(idxTagOpen);
      int idxTagClose = tagOpen.indexOf(">");
      if(idxTagClose<0)
        break;
      idxTagClose++;
      while(tagOpen.indexOf(idxTagClose)==' ')
        idxTagClose++;
      text = text.substring(0,idxTagOpen).trim() +" "+ tagOpen.substring(idxTagClose).trim();
      if(Config.LOGD) Log.i(LOGTAG, "ripTags tmp  ["+text+"]");
      idxTagOpen = text.indexOf("<");
    }
    if(Config.LOGD) Log.i(LOGTAG, "ripTags done ["+text+"]");
    return text;
  }
}

