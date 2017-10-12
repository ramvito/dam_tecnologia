package com.example.holamundonfc;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;


@SuppressLint({ "ParserError", "ParserError" })
public class MainActivity extends Activity{

    NfcAdapter adapter;
    PendingIntent pendingIntent;
    IntentFilter writeTagFilters[];
    boolean writeMode;
    Tag mytag;
    Context ctx;
    CheckBox readOnlyCheckBox;
    TextView tagIdText;
    TextView readOnlyText;
    TextView tagSpecsText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx=this;
        Button btnWrite = (Button) findViewById(R.id.button);
        final TextView message = (TextView)findViewById(R.id.edit_message);

        readOnlyCheckBox = findViewById(R.id.readOnlyCheckBox);
        tagIdText = findViewById(R.id.tagIdText);
        readOnlyText = findViewById(R.id.readOnlyText);
        tagSpecsText = findViewById(R.id.tagSpecsText);

        btnWrite.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {

                try {
                    if(mytag==null){
                        Toast.makeText(ctx, ctx.getString(R.string.error_detected), Toast.LENGTH_LONG ).show();
                    }else{
                        write(message.getText().toString(),mytag);
                    }
                } catch (IOException e) {
                    Toast.makeText(ctx, ctx.getString(R.string.error_writing), Toast.LENGTH_LONG ).show();
                    e.printStackTrace();
                } catch (FormatException e) {
                    Toast.makeText(ctx, ctx.getString(R.string.error_writing), Toast.LENGTH_LONG ).show();
                    e.printStackTrace();
                }
            }
        });

        adapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writeTagFilters = new IntentFilter[] { tagDetected };

    }



    private void write(String text, Tag tag) throws IOException, FormatException {

        NdefRecord[] records = { createRecord(text) };
        NdefMessage  message = new NdefMessage(records);

        // Obtener una instancia de Ndef para el tag.
        Ndef ndef = Ndef.get(tag);
        // Abrir la conexión
        ndef.connect();

        // Escribir el mensaje
        ndef.writeNdefMessage(message);
        Toast.makeText(ctx, ctx.getString(R.string.ok_writing), Toast.LENGTH_LONG ).show();

        // Poner en modo solo lectura
        if (readOnlyCheckBox.isChecked() && ndef.canMakeReadOnly()) {

                ndef.makeReadOnly();
        }

        // Cerrar la conexión
        ndef.close();
    }



    private NdefRecord createRecord(String text) throws UnsupportedEncodingException {
        String lang       = "en";
        byte[] textBytes  = text.getBytes();
        byte[] langBytes  = lang.getBytes("US-ASCII");
        int    langLength = langBytes.length;
        int    textLength = textBytes.length;
        byte[] payload    = new byte[1 + langLength + textLength];

        // set status byte (see NDEF spec for actual bits)
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1,              langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        // https://flomio.com/2012/05/ndef-basics/
        NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,  NdefRecord.RTD_TEXT,  new byte[0], payload);

        return recordNFC;
    }


    @Override
    protected void onNewIntent(Intent intent){
        if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){

            mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            Ndef ndef = Ndef.get(mytag);

            if (ndef == null) {

                // NDEF no soportado
                Toast.makeText(this, "NDEF not supported!", Toast.LENGTH_SHORT);

            } else {

                NdefMessage ndefMessage = ndef.getCachedNdefMessage();

                NdefRecord[] records = ndefMessage.getRecords();
                for (NdefRecord ndefRecord : records) {
                    if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(ndefRecord.getType(), NdefRecord.RTD_TEXT)) {
                        try {

                            // Mostramos el texto
                            String text =  readText(ndefRecord);
                            Toast.makeText(this, this.getString(R.string.ok_detection) + text, Toast.LENGTH_LONG ).show();

                            // Mostramos las tecnologias del tag
                            String specs = "";
                            for (int i = 0; i<mytag.getTechList().length; i++) {
                                specs += "NFC tag TECH:" + mytag.getTechList()[i] + System.getProperty("line.separator");
                            }
                            tagSpecsText.setText(specs);

                            // Mostramos si es read only
                            String readOnly = "NFC tag READONLY cap: ";
                            readOnly += ndef.canMakeReadOnly() ? "true": "false";
                            readOnlyText.setText(readOnly);

                            // Mostramos el id del tag
                            tagIdText.setText("NFC tag ID: " + byteArrayToHexString(mytag.getId()));

                        } catch (UnsupportedEncodingException e) {
                            Toast.makeText(this, "NDEF not supported!", Toast.LENGTH_SHORT);
                        }
                    }
                }
            }
        }
    }

    public static String byteArrayToHexString(byte[] array) {
        StringBuffer hexString = new StringBuffer();
        for (byte b : array) {
            int intVal = b & 0xff;
            if (intVal < 0x10)
                hexString.append("0");
            hexString.append(Integer.toHexString(intVal));
        }
        return hexString.toString();
    }

    private String readText(NdefRecord record) throws UnsupportedEncodingException {
        /*
         * See NFC forum specification for "Text Record Type Definition" at 3.2.1
         *
         * http://www.nfc-forum.org/specs/
         *
         * bit_7 defines encoding
         * bit_6 reserved for future use, must be 0
         * bit_5..0 length of IANA language code
         */

        byte[] payload = record.getPayload();

        // Get the Text Encoding
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16";

        // Get the Language Code
        int languageCodeLength = payload[0] & 0063;

        // String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
        // e.g. "en"

        // Get the Text
        return new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
    }

    @Override
    public void onPause(){
        super.onPause();
        WriteModeOff();
    }

    @Override
    public void onResume(){
        super.onResume();
        WriteModeOn();
    }

    private void WriteModeOn(){
        writeMode = true;

        // Cuando la app esta abierta, no queremos que vuelva a abrirnos otra vez la app, si no
        // que nos envíe el tag directamente a la app abierta
        adapter.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null);
    }

    private void WriteModeOff(){
        writeMode = false;

        // Si la app no está abierta, queremos que se lance la app indicada
        adapter.disableForegroundDispatch(this);
    }
}