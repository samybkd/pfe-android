package com.example.projet_pfe_android;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.projet_pfe_android.Adapters.TransactionAdapter;
import com.example.projet_pfe_android.Model.Product;
import com.example.projet_pfe_android.Model.Transaction;
import com.example.projet_pfe_android.Model.TransactionLine;
import com.example.projet_pfe_android.Util.JavaUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import static org.apache.commons.lang3.Validate.notNull;

public class CurrentTransactionActivity extends AppCompatActivity {

    private AppViewModel viewModel;
    private TransactionAdapter adapter;
    private int type;
    private List<TransactionLine> transactionLines;
    private double totalAmount;
    private TransactionLine selectedTransaction;
    private ConstraintLayout clValidationWindow;
    private FrameLayout flValidationWindow;
    private TextView tvProductName;
    private AppCompatEditText etQuantity;
    private Button bValider, bAnnuler;

    private TextView tvTotalAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_current_transaction);

        init();

        setupViewModel();

        setupRecycler();
        setupValidationWindow();

    }

    private void setupViewModel() {
        viewModel = ViewModelProviders.of(this).get(AppViewModel.class);

        viewModel.getCurrentTransactionProducts().observe(this, new Observer<List<Product>>() {
            @Override
            public void onChanged(List<Product> products) {
                transactionLines = TransactionLine.toTransactionLine(products, type);
                Log.d("price#", "type : "+type);
                setTotalAmount();
                adapter.submitList(transactionLines);
            }
        });

        viewModel.getAllTransactionLines().observe(this, new Observer<List<TransactionLine>>() {
            @Override
            public void onChanged(List<TransactionLine> transactionLines) {
                for (TransactionLine t : transactionLines)
                    Log.d("tnx", t.toString());
            }
        });
    }

    private void init() {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Transaction");
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.ic_home);

        Spinner spinner = findViewById(R.id.sp_type);
        type = getIntent().getIntExtra(JavaUtil.TRANSACTION_TYPE_KEY, JavaUtil.NO_RESULT);
        if (type != JavaUtil.NO_RESULT) {
            spinner.setSelection(type);
        }

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                type = i;
//                transactionLines = TransactionLine.toTransactionLine(viewModel.getCurrentTransactionProducts().getValue(), type);
                for (TransactionLine transactionLine : transactionLines) {
                    double amount=0;
                    switch (type) {
                        case Transaction.TYPE_VENTE:
                            amount = transactionLine.getQuantity() * transactionLine.getSalesPrice();
                            break;
                        case Transaction.TYPE_RECEPTION:
                            amount = transactionLine.getQuantity() * transactionLine.getPrice();
                            break;
                    }
                    transactionLine.setAmount(amount);
                    transactionLine.setType(type);
                }
                setTotalAmount();
                adapter.submitList(transactionLines);
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        FloatingActionButton fab = findViewById(R.id.floatingActionButton1);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(CurrentTransactionActivity.this, ListProduit.class);
                intent.putExtra(JavaUtil.TRANSACTION_TYPE_KEY, type);
                startActivity(intent);
            }
        });

        tvTotalAmount = findViewById(R.id.tv_total);
    }

    private void setupRecycler() {
        adapter = new TransactionAdapter(new TransactionAdapter.TransactionAdapterListener() {
            @Override
            public void onEdit(TransactionLine transactionLine) {
                selectedTransaction= transactionLine;
                showValidationWindow();
            }
        });

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        /*new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    viewModel.deleteTransactionLine(viewModel.getAllTransactionLines().getValue().get(position));
                }
            }
       }).attachToRecyclerView(recyclerView);*/
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.transaction_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.validate:
                // TODO: 29/05/2019 [for Yacine] implement a SnackBar to confirm validation
                float caisse = JavaUtil.getCaisse(this);
                if (caisse < totalAmount && type == Transaction.TYPE_RECEPTION) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setCancelable(true);
                    builder.setMessage("Vous n'avez pas assez d'argent en caisse");
                    builder.create().show();
                    break;
                }
                if (type == Transaction.TYPE_VENTE) {
                    List<Product> productsUnderStockLimit = new ArrayList<>();
                    for (TransactionLine transactionLine : transactionLines) {
                        List<Product> products = viewModel.getCurrentTransactionProducts().getValue();
                        notNull(products, "[CurrentTransactionActivity][onOptionsItemSelected] products is null");
                        for (Product product : products) {
                            if (transactionLine.getProductId() == product.getId() && transactionLine.getQuantity() > product.getAvailableQty()) {
                                productsUnderStockLimit.add(product);
                            }
                        }
                    }

                    if (!productsUnderStockLimit.isEmpty()) {
                        String failsDetails = "Vous n'avez pas assez de stock pour ce(s) produit(s) : \n ";
                        String failMsgTemplate = "Produit : %s , quantit?? disponible : %s %s";
                        for (Product product : productsUnderStockLimit) {
                            failsDetails = failsDetails.concat(
                                    String.format(
                                            failMsgTemplate,
                                            product.getName(),
                                            product.getAvailableQty(),
                                            product.getUOM()
                                    )
                            );
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setCancelable(true);
                        builder.setMessage(failsDetails);
                        builder.create().show();
                        break;
                    }
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setCancelable(true);
                builder.setMessage("??tes vous s??r de vouloir valider cette transaction ?");
                builder.setPositiveButton("Confirmer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        commitTransaction();
                    }
                });
                builder.create().show();
                break;
            case R.id.empty_transaction:
                // TODO: 29/05/2019 [for Yacine] SnackBar to confirm deleting all transaction lines from current transaction
                AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
                builder1.setCancelable(true);
                builder1.setMessage("??tes vous s??r de vouloir vider la transaction ?");
                builder1.setPositiveButton("Confirmer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        viewModel.emptyCurrentTransaction();
                    }
                });
                builder1.create().show();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void commitTransaction() {
//         setTransactionDate, totalAmount
        long date = Calendar.getInstance(TimeZone.getDefault()).getTimeInMillis();
        viewModel.getCurrentTransaction().setDate(date);
        viewModel.getCurrentTransaction().setTotalAmount(totalAmount);
        viewModel.getCurrentTransaction().setType(type);

//         insert Transaction and get its id
        int transactionId = viewModel.insertCurrentTransaction();

//         set transactionId in lines

        for (TransactionLine t : transactionLines) {
            t.setTransactionId(transactionId);
            t.setDate(date);
            t.setType(type);
        }

//         insert lines
        viewModel.insertTransactionLines(transactionLines);

//        Update product availableQty
        List<Product> products = viewModel.getCurrentTransactionProducts().getValue();
        int i = (type == Transaction.TYPE_VENTE) ? -1 : 1;
        for (Product p : products)
            for (TransactionLine t : transactionLines)
                if (p.getId() == t.getProductId())
                    p.setAvailableQty(p.getAvailableQty() + i * t.getQuantity());
        viewModel.updateProducts(products);

//        Update caisse
        float nouvellecaisse = JavaUtil.getCaisse(this) + (float) ((-i) * totalAmount);
        Toast.makeText(this, "NC = " + nouvellecaisse, Toast.LENGTH_SHORT).show();
        JavaUtil.saveCaisse(this, nouvellecaisse);

//        Empty current transaction and reinitialize products transactionQty
        viewModel.emptyCurrentTransaction();


        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    public void setTotalAmount() {
        if (transactionLines.size() == 0) {
            totalAmount = 0;
            tvTotalAmount.setText(JavaUtil.currencyString(totalAmount));
            return;
        }

        double totalAmount = 0;
        for (TransactionLine t : transactionLines) {
            totalAmount += t.amount;
        }
        this.totalAmount = totalAmount;
        tvTotalAmount.setText(JavaUtil.currencyString(totalAmount));
    }

    private void setupValidationWindow() {
        clValidationWindow = findViewById(R.id.cl_validation_window1);
        flValidationWindow = findViewById(R.id.fl_validation_window1);
        tvProductName = findViewById(R.id.tv_nom_produit1);
        etQuantity = findViewById(R.id.et_quantity1);
        bValider = findViewById(R.id.b_valider1);
        bAnnuler = findViewById(R.id.b_annuler1);
        bValider.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Add product with qty to transaction list
                try {
                    float qty = Float.parseFloat(etQuantity.getText().toString());
                    if (selectedTransaction != null) {
                        selectedTransaction.setQuantity(qty);
                        if (selectedTransaction.getType() == Transaction.TYPE_VENTE) {
                            selectedTransaction.setAmount(qty* selectedTransaction.getSalesPrice());
                        }
                        else if (selectedTransaction.getType() == Transaction.TYPE_RECEPTION) {
                            selectedTransaction.setAmount(qty* selectedTransaction.getPrice());
                        }
//                        viewModel.addToCurrentTransaction(selectedProduct);
                        viewModel.updateTransactionLne(selectedTransaction);
                        adapter.notifyDataSetChanged();
                        tvTotalAmount.setText(String.valueOf(totalAmount));
                        setTotalAmount();
                        // Toast.makeText(C.this, "Update ID : " + selectedProduct.getId(), Toast.LENGTH_SHORT).show();
                    }
                    // Toast.makeText(ListProduit.this, "Transaction mise ?? jour.", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Log.d("#EXP", "ListProduit.bValider.onClick: " + e.getMessage());
                }
                hideValidationWindow();

            }
        });
        bAnnuler.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                hideValidationWindow();
            }
        });
    }

    private void hideValidationWindow() {
        clValidationWindow.setVisibility(View.GONE);
        flValidationWindow.setVisibility(View.GONE);
    }

    private void showValidationWindow() {
        clValidationWindow.setVisibility(View.VISIBLE);
        flValidationWindow.setVisibility(View.VISIBLE);
        tvProductName.setText(selectedTransaction.getProductName());
        etQuantity.setText(Float.toString(selectedTransaction.getQuantity()));
    }
}