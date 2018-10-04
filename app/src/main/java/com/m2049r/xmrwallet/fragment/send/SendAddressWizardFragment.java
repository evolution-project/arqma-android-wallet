/*
 * Copyright (c) 2017 m2049r
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m2049r.xmrwallet.fragment.send;

import android.content.Context;
import android.os.Bundle;
import android.support.design.widget.TextInputLayout;
import android.support.v7.widget.CardView;
import android.text.Editable;
import android.text.Html;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.m2049r.xmrwallet.R;
import com.m2049r.xmrwallet.data.BarcodeData;
import com.m2049r.xmrwallet.data.TxData;
import com.m2049r.xmrwallet.model.Wallet;
import com.m2049r.xmrwallet.util.Helper;
import com.m2049r.xmrwallet.util.OpenAliasHelper;

import java.util.Map;

import timber.log.Timber;

public class SendAddressWizardFragment extends SendWizardFragment {

    static final int INTEGRATED_ADDRESS_LENGTH = 109;

    public static SendAddressWizardFragment newInstance(Listener listener) {
        SendAddressWizardFragment instance = new SendAddressWizardFragment();
        instance.setSendListener(listener);
        return instance;
    }

    Listener sendListener;

    public SendAddressWizardFragment setSendListener(Listener listener) {
        this.sendListener = listener;
        return this;
    }

    public interface Listener {
        void setBarcodeData(BarcodeData data);

        TxData getTxData();
    }

    private EditText etDummy;
    private TextInputLayout etAddress;
    private TextInputLayout etPaymentId;
    private Button bPaymentId;
    private CardView cvScan;
    private View tvPaymentIdIntegrated;
    private View llPaymentId;
	
	private boolean resolvingOA = false;

    OnScanListener onScanListener;

    public interface OnScanListener {
        void onScan();

        BarcodeData popScannedData();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Timber.d("onCreateView() %s", (String.valueOf(savedInstanceState)));

        View view = inflater.inflate(R.layout.fragment_send_address, container, false);

        tvPaymentIdIntegrated = view.findViewById(R.id.tvPaymentIdIntegrated);
        llPaymentId = view.findViewById(R.id.llPaymentId);

        etAddress = (TextInputLayout) view.findViewById(R.id.etAddress);
        etAddress.getEditText().setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etAddress.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_NEXT)) {
                String dnsOA = dnsFromOpenAlias(etAddress.getEditText().getText().toString());
                    Timber.d("OpenAlias is %s", dnsOA);
                    if (dnsOA != null) {
                        processOpenAlias(dnsOA);
                    } else if (checkAddress()) {
                    if (llPaymentId.getVisibility() == View.VISIBLE) {
                        etPaymentId.requestFocus();
                    } else {
                        etDummy.requestFocus();
                        Helper.hideKeyboard(getActivity());
                    }
                }
                return true;
            }
            return false;
        });
        etAddress.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                etAddress.setError(null);
                if (isIntegratedAddress()) {
                    Timber.d("isIntegratedAddress");
                    etPaymentId.getEditText().getText().clear();
                    llPaymentId.setVisibility(View.INVISIBLE);
                    tvPaymentIdIntegrated.setVisibility(View.VISIBLE);
                } else {
                    Timber.d("isStandardAddress");
                    llPaymentId.setVisibility(View.VISIBLE);
                    tvPaymentIdIntegrated.setVisibility(View.INVISIBLE);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });


        etPaymentId = (TextInputLayout) view.findViewById(R.id.etPaymentId);
        etPaymentId.getEditText().setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etPaymentId.getEditText().setOnEditorActionListener((v, actionId, event) -> {
            if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
                if (checkPaymentId()) {
                    etDummy.requestFocus();
                    Helper.hideKeyboard(getActivity());
                }
                return true;
            }
            return false;
        });
        etPaymentId.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                etPaymentId.setError(null);
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        bPaymentId = (Button) view.findViewById(R.id.bPaymentId);
        bPaymentId.setOnClickListener(v -> etPaymentId.getEditText().setText((Wallet.generatePaymentId())));

        cvScan = (CardView) view.findViewById(R.id.bScan);
        cvScan.setOnClickListener(v -> onScanListener.onScan());

        etDummy = (EditText) view.findViewById(R.id.etDummy);
        etDummy.setRawInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        etDummy.requestFocus();
        Helper.hideKeyboard(getActivity());

        return view;
    }
	
	private void processOpenAlias(String dnsOA) {
        if (resolvingOA) return; // already resolving - just wait
        if (dnsOA != null) {
            resolvingOA = true;
            etAddress.setError(getString(R.string.send_address_resolve_openalias));
            OpenAliasHelper.resolve(dnsOA, new OpenAliasHelper.OnResolvedListener() {
                @Override
                public void onResolved(Map<BarcodeData.Asset, BarcodeData> dataMap) {
                    resolvingOA = false;
                    BarcodeData barcodeData = dataMap.get(BarcodeData.Asset.ARQ);
                    if (barcodeData != null) {
                        Timber.d("DNSSEC=%b, %s", barcodeData.isSecure, barcodeData.address);
                        processScannedData(barcodeData);
                        etDummy.requestFocus();
                        Helper.hideKeyboard(getActivity());
                    } else {
                        etAddress.setError(getString(R.string.send_address_not_openalias));
                        Timber.d("NO Arqma OpenAlias Found!!");
                    }
                }
                 @Override
                public void onFailure() {
                    resolvingOA = false;
                    etAddress.setError(getString(R.string.send_address_not_openalias));
                    Timber.e("OA FAILED");
                }
            });
        } // else ignore
    }

    private boolean checkAddressNoError() {
        String address = etAddress.getEditText().getText().toString();
        return Wallet.isAddressValid(address);
    }

    private boolean checkAddress() {
        boolean ok = checkAddressNoError();
        if (!ok) {
            etAddress.setError(getString(R.string.send_address_invalid));
        } else {
            etAddress.setError(null);
        }
        return ok;
    }

    private boolean isIntegratedAddress() {
        String address = etAddress.getEditText().getText().toString();
        return (address.length() == INTEGRATED_ADDRESS_LENGTH)
                && Wallet.isAddressValid(address);
    }

    private boolean checkPaymentId() {
        String paymentId = etPaymentId.getEditText().getText().toString();
        boolean ok = paymentId.isEmpty() || Wallet.isPaymentIdValid(paymentId);
        if (!ok) {
            etPaymentId.setError(getString(R.string.receive_paymentid_invalid));
        } else {
            if (!paymentId.isEmpty() && isIntegratedAddress()) {
                ok = false;
                etPaymentId.setError(getString(R.string.receive_integrated_paymentid_invalid));
            } else {
                etPaymentId.setError(null);
            }
        }
        return ok;
    }
	
	private void shakeAddress() {
        etAddress.startAnimation(Helper.getShakeAnimation(getContext()));
    }

    @Override
    public boolean onValidateFields() {
        boolean ok = true;
        if (!checkAddressNoError()) {
            shakeAddress();
            ok = false;
			String dnsOA = dnsFromOpenAlias(etAddress.getEditText().getText().toString());
            Timber.d("OpenAlias is %s", dnsOA);
            if (dnsOA != null) {
                processOpenAlias(dnsOA);
            }
        }
        if (!checkPaymentId()) {
            etPaymentId.startAnimation(Helper.getShakeAnimation(getContext()));
            ok = false;
        }
        if (!ok) return false;
        if (sendListener != null) {
            TxData txData = sendListener.getTxData();
            txData.setDestinationAddress(etAddress.getEditText().getText().toString());
            txData.setPaymentId(etPaymentId.getEditText().getText().toString());
        }
        return true;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof OnScanListener) {
            onScanListener = (OnScanListener) context;
        } else {
            throw new ClassCastException(context.toString() + " must implement ScanListener");
        }
    }

    // QR Scan Stuff

    @Override
    public void onResume() {
        super.onResume();
        Timber.d("onResume");
        BarcodeData data = onScanListener.popScannedData();
        sendListener.setBarcodeData(data);
        if (data != null) {
            Timber.d("GOT DATA");
            String scannedAddress = data.address;
            if (scannedAddress != null) {
                etAddress.getEditText().setText(scannedAddress);
                if (checkAddress()) {
                    if (!barcodeData.isSecure)
                        etAddress.setError(getString(R.string.send_address_no_dnssec));
                    else
                        etAddress.setError(getString(R.string.send_address_openalias));
                }
            } else {
                etAddress.getEditText().getText().clear();
                etAddress.setError(null);
            }
            String scannedPaymentId = data.paymentId;
            if (scannedPaymentId != null) {
                etPaymentId.getEditText().setText(scannedPaymentId);
                checkPaymentId();
            } else {
                etPaymentId.getEditText().getText().clear();
                etPaymentId.setError(null);
            }
        }
    }

    @Override
    public void onResumeFragment() {
        super.onResumeFragment();
        Timber.d("onResumeFragment()");
        Helper.hideKeyboard(getActivity());
        etDummy.requestFocus();
    }
	
	String dnsFromOpenAlias(String openalias) {
        Timber.d("checking openalias candidate %s", openalias);
        if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias;
        if (Patterns.EMAIL_ADDRESS.matcher(openalias).matches()) {
            openalias = openalias.replaceFirst("@", ".");
            if (Patterns.DOMAIN_NAME.matcher(openalias).matches()) return openalias;
        }
        return null; // not an openalias
    }
}