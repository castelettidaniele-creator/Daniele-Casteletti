package it.caste.inventariocaste

import android.app.*
import android.os.Bundle
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.text.InputType
import android.view.*
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

data class Item(val id:Long=0,val name:String="",val category:String="",val qty:Double=0.0,
 val unit:String="pz",val location:String="",val min:Double=0.0,val price:Double=0.0,
 val photo:String="",val notes:String="")

class DB(c:Context):SQLiteOpenHelper(c,"caste.db",null,1) {
 override fun onCreate(d:SQLiteDatabase) {
  d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,category TEXT,qty REAL,unit TEXT,location TEXT,min REAL,price REAL,photo TEXT,notes TEXT)")
  d.execSQL("CREATE TABLE cats(name TEXT PRIMARY KEY)")
  d.execSQL("CREATE TABLE buys(id INTEGER PRIMARY KEY AUTOINCREMENT,item TEXT,date TEXT,qty REAL,total REAL,supplier TEXT)")
  listOf("Macchinari","Tornio","Stampa 3D","Utensili","Legname e materiali","Minuteria","Materiale elettrico","Attrezzatura e accessori","Materiale di consumo").forEach { d.execSQL("INSERT OR IGNORE INTO cats VALUES(?)",arrayOf(it)) }
 }
 override fun onUpgrade(d:SQLiteDatabase,o:Int,n:Int){}
 fun cats():List<String>{val a=mutableListOf<String>();readableDatabase.rawQuery("SELECT name FROM cats ORDER BY name",null).use{while(it.moveToNext())a.add(it.getString(0))};return a}
 fun addCat(s:String){writableDatabase.execSQL("INSERT OR IGNORE INTO cats VALUES(?)",arrayOf(s.trim()))}
 fun items(q:String=""):List<Item>{val a=mutableListOf<Item>();readableDatabase.rawQuery("SELECT id,name,category,qty,unit,location,min,price,photo,notes FROM items WHERE name LIKE ? OR category LIKE ? OR location LIKE ? ORDER BY name",arrayOf("%$q%","%$q%","%$q%")).use{c->while(c.moveToNext())a.add(Item(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getDouble(6),c.getDouble(7),c.getString(8),c.getString(9)))};return a}
 fun save(i:Item){val v=arrayOf(i.name,i.category,i.qty.toString(),i.unit,i.location,i.min.toString(),i.price.toString(),i.photo,i.notes);if(i.id==0L)writableDatabase.execSQL("INSERT INTO items(name,category,qty,unit,location,min,price,photo,notes) VALUES(?,?,?,?,?,?,?,?,?)",v) else writableDatabase.execSQL("UPDATE items SET name=?,category=?,qty=?,unit=?,location=?,min=?,price=?,photo=?,notes=? WHERE id=?",v+arrayOf(i.id.toString()))}
 fun delete(id:Long){writableDatabase.delete("items","id=?",arrayOf(id.toString()))}
 fun buy(i:Item,date:String,q:Double,total:Double,supplier:String){writableDatabase.execSQL("INSERT INTO buys(item,date,qty,total,supplier) VALUES(?,?,?,?,?)",arrayOf(i.name,date,q,total,supplier));save(i.copy(qty=i.qty+q,price=if(q>0)total/q else i.price))}
 fun buys():List<String>{val a=mutableListOf<String>();readableDatabase.rawQuery("SELECT item,date,qty,total,supplier FROM buys ORDER BY id DESC",null).use{while(it.moveToNext())a.add("${it.getString(0)} • ${it.getString(1)} • ${it.getDouble(2)} • €${it.getDouble(3)} • ${it.getString(4)}")};return a}
}
class MainActivity:Activity(){
 lateinit var db:DB; lateinit var box:LinearLayout; var photo=""; var editId=0L; var csv=""
 fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
 fun t(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setTextColor(0xff302820.toInt());setPadding(4,dp(7),4,dp(7))}
 fun b(s:String,f:()->Unit)=Button(this).apply{text=s;setOnClickListener{f()}}
 fun page(title:String){val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(0xfff7f3ef.toInt())};root.addView(TextView(this).apply{text=title;textSize=23f;setTextColor(-1);setBackgroundColor(0xff5d4037.toInt());setPadding(dp(16),dp(18),dp(16),dp(18))});val sc=ScrollView(this);box=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(10),dp(14),dp(20))};sc.addView(box);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));setContentView(root)}
 override fun onCreate(s:Bundle?){super.onCreate(s);db=DB(this);home()}
 fun home(){page("Inventario Caste");val all=db.items();box.addView(t("Il tuo laboratorio, sempre sotto controllo.",18f));box.addView(t("Articoli: ${all.size}     Scorte basse: ${all.count{it.min>0&&it.qty<=it.min}}",18f));box.addView(b("＋ Aggiungi articolo"){editor(null)});box.addView(b("▤ Tutti gli articoli"){items()});box.addView(b("▦ Categorie"){categories()});box.addView(b("€ Storico acquisti"){purchases()});box.addView(b("⚠ Scorte da reintegrare"){items(true)});box.addView(b("⇧ Esporta inventario CSV"){export()});box.addView(t("Categorie iniziali",18f));db.cats().forEach{box.addView(t("• $it"))}}
 fun items(low:Boolean=false){page(if(low)"Scorte basse" else "Tutti gli articoli");val search=EditText(this).apply{hint="Cerca nome, categoria o posizione"};box.addView(search);val list=LinearLayout(this).apply{orientation=1};box.addView(list)
  fun refresh(){list.removeAllViews();db.items(search.text.toString()).filter{!low||(it.min>0&&it.qty<=it.min)}.forEach{i->list.addView(t("${i.name} • ${i.qty} ${i.unit}"+if(i.min>0&&i.qty<=i.min)" ⚠ SCORTA BASSA" else "",18f));list.addView(t("${i.category} | ${i.location} | €${i.price}"));val row=LinearLayout(this).apply{orientation=0};row.addView(b("Modifica"){editor(i)},LinearLayout.LayoutParams(0,-2,1f));row.addView(b("Acquisto"){buyDialog(i)},LinearLayout.LayoutParams(0,-2,1f));row.addView(b("Elimina"){AlertDialog.Builder(this@MainActivity).setMessage("Eliminare ${i.name}?").setNegativeButton("No",null).setPositiveButton("Sì"){_,_->db.delete(i.id);refresh()}.show()},LinearLayout.LayoutParams(0,-2,1f));list.addView(row);list.addView(t("────────────────────"))}}
  search.addTextChangedListener(object:android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){};override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){refresh()};override fun afterTextChanged(e:android.text.Editable?){}});refresh();box.addView(b("＋ Aggiungi articolo"){editor(null)});box.addView(b("← Home"){home()})}
 fun editor(i:Item?){editId=i?.id?:0L;photo=i?.photo?:"";page(if(i==null)"Nuovo articolo" else "Modifica articolo")
  fun field(label:String,value:String="",num:Boolean=false):EditText{box.addView(t(label));return EditText(this).apply{setText(value);hint=label;if(num)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}.also{box.addView(it)}}
  val name=field("Nome articolo",i?.name?:"");box.addView(t("Categoria"));val cats=db.cats();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cats);sp.setSelection((cats.indexOf(i?.category)).coerceAtLeast(0));box.addView(sp)
  val qty=field("Quantità",i?.qty?.toString()?: "1",true);val unit=field("Unità (pz, m, kg, confezioni)",i?.unit?:"pz");val loc=field("Posizione precisa",i?.location?:"");val min=field("Scorta minima",i?.min?.toString()?:"0",true);val price=field("Costo unitario (€)",i?.price?.toString()?:"0",true);val notes=field("Note",i?.notes?:"")
  box.addView(t(if(photo.isBlank())"Nessuna foto selezionata" else "Foto associata"));box.addView(b("📷 Scegli foto"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},41)})
  box.addView(b("Salva articolo"){if(name.text.isBlank()){name.error="Inserisci il nome";return@b};db.save(Item(editId,name.text.toString(),sp.selectedItem.toString(),qty.text.toString().toDoubleOrNull()?:0.0,unit.text.toString(),loc.text.toString(),min.text.toString().toDoubleOrNull()?:0.0,price.text.toString().toDoubleOrNull()?:0.0,photo,notes.text.toString()));items()});box.addView(b("Annulla"){home()})}
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==41&&c==RESULT_OK){d?.data?.let{u->try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};photo=u.toString();Toast.makeText(this,"Foto associata",Toast.LENGTH_SHORT).show()}};if(r==42&&c==RESULT_OK)d?.data?.let{contentResolver.openOutputStream(it)?.bufferedWriter()?.use{w->w.write(csv)};Toast.makeText(this,"CSV esportato",Toast.LENGTH_SHORT).show()}}
 fun categories(){page("Categorie");db.cats().forEach{box.addView(t("• $it"))};box.addView(b("＋ Nuova categoria"){val e=EditText(this).apply{hint="Nome categoria"};AlertDialog.Builder(this).setTitle("Nuova categoria").setView(e).setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->if(e.text.isNotBlank()){db.addCat(e.text.toString());categories()}}.show()});box.addView(b("← Home"){home()})}
 fun buyDialog(i:Item){val l=LinearLayout(this).apply{orientation=1;setPadding(dp(16),0,dp(16),0)};val date=EditText(this).apply{setText(SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(Date()))};val q=EditText(this).apply{hint="Quantità";inputType=2};val total=EditText(this).apply{hint="Totale €";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};val supplier=EditText(this).apply{hint="Fornitore"};listOf(date,q,total,supplier).forEach{l.addView(it)};AlertDialog.Builder(this).setTitle("Registra acquisto").setView(l).setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->db.buy(i,date.text.toString(),q.text.toString().toDoubleOrNull()?:0.0,total.text.toString().toDoubleOrNull()?:0.0,supplier.text.toString());items()}.show()}
 fun purchases(){page("Storico acquisti");val a=db.buys();if(a.isEmpty())box.addView(t("Nessun acquisto registrato"));a.forEach{box.addView(t(it))};box.addView(b("← Home"){home()})}
 fun export(){csv=buildString{append("Nome;Categoria;Quantita;Unita;Posizione;ScortaMinima;CostoUnitario;Note\n");db.items().forEach{append("${it.name};${it.category};${it.qty};${it.unit};${it.location};${it.min};${it.price};${it.notes}\n")}};startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/csv";putExtra(Intent.EXTRA_TITLE,"inventario_caste.csv")},42)}
}
