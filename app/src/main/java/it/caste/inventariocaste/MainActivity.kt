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
 override fun onOpen(d:SQLiteDatabase){super.onOpen(d);if(!d.isReadOnly)d.execSQL("INSERT OR IGNORE INTO cats(name) VALUES('Vernici')")}
 fun cats():List<String>{val a=mutableListOf<String>();readableDatabase.rawQuery("SELECT name FROM cats ORDER BY name",null).use{while(it.moveToNext())a.add(it.getString(0))};return a}
 fun addCat(s:String){writableDatabase.execSQL("INSERT OR IGNORE INTO cats VALUES(?)",arrayOf(s.trim()))}
 fun items(q:String=""):List<Item>{val a=mutableListOf<Item>();readableDatabase.rawQuery("SELECT id,name,category,qty,unit,location,min,price,photo,notes FROM items WHERE name LIKE ? OR category LIKE ? OR location LIKE ? ORDER BY name",arrayOf("%$q%","%$q%","%$q%")).use{c->while(c.moveToNext())a.add(Item(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getDouble(6),c.getDouble(7),c.getString(8),c.getString(9)))};return a}
 fun save(i:Item){val v=arrayOf(i.name,i.category,i.qty.toString(),i.unit,i.location,i.min.toString(),i.price.toString(),i.photo,i.notes);if(i.id==0L)writableDatabase.execSQL("INSERT INTO items(name,category,qty,unit,location,min,price,photo,notes) VALUES(?,?,?,?,?,?,?,?,?)",v) else writableDatabase.execSQL("UPDATE items SET name=?,category=?,qty=?,unit=?,location=?,min=?,price=?,photo=?,notes=? WHERE id=?",v+arrayOf(i.id.toString()))}
 fun adjust(i:Item,n:Double){save(i.copy(qty=(i.qty+n).coerceAtLeast(0.0)))}
 fun delete(id:Long){writableDatabase.delete("items","id=?",arrayOf(id.toString()))}
 fun buy(i:Item,date:String,q:Double,total:Double,supplier:String){writableDatabase.execSQL("INSERT INTO buys(item,date,qty,total,supplier) VALUES(?,?,?,?,?)",arrayOf(i.name,date,q,total,supplier));save(i.copy(qty=i.qty+q,price=if(q>0)total/q else i.price))}
 fun buys():List<String>{val a=mutableListOf<String>();readableDatabase.rawQuery("SELECT item,date,qty,total,supplier FROM buys ORDER BY id DESC",null).use{while(it.moveToNext())a.add("${it.getString(0)} • ${it.getString(1)} • ${it.getDouble(2)} • €${it.getDouble(3)} • ${it.getString(4)}")};return a}
}
class MainActivity:Activity(){
 lateinit var db:DB; lateinit var box:LinearLayout; var photo=""; var editId=0L; var csv=""
 fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
 fun t(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setTextColor(0xff302820.toInt());setPadding(4,dp(7),4,dp(7))}
 fun b(s:String,f:()->Unit)=Button(this).apply{text=s;setOnClickListener{f()}}
 fun page(title:String){val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(0xfff7f3ef.toInt())};root.addView(TextView(this).apply{text=title;textSize=23f;setTextColor(-1);setBackgroundColor(0xff5d4037.toInt());setPadding(dp(16),dp(18),dp(16),dp(18))});val sc=ScrollView(this);box=LinearLayout(this).apply{orientation=1;setPadding(dp(14),dp(10),dp(14),dp(20))};sc.addView(box);root.addView(sc,LinearLayout.LayoutParams(-1,0,1f));root.setOnApplyWindowInsetsListener{v,w->v.setPadding(0,w.systemWindowInsetTop,0,w.systemWindowInsetBottom);w};setContentView(root)}
 override fun onCreate(s:Bundle?){super.onCreate(s);db=DB(this);home()}
 fun iconFor(name:String,category:String):String {
  val v=(name+" "+category).lowercase(Locale.ROOT)
  return when {
   listOf("cuscinet","bearing").any{v.contains(it)} -> "⚙"
   listOf("vite","bullon","dado","rondell").any{v.contains(it)} -> "🔩"
   listOf("fascett","cavo","connettor","led","elettric").any{v.contains(it)} -> "🔌"
   listOf("vernice","smalto","impregnant","pittura").any{v.contains(it)} -> "🎨"
   listOf("legno","pino","tavola","asse").any{v.contains(it)} -> "🪵"
   listOf("stampa 3d","filament","pla","stampante").any{v.contains(it)} -> "🖨"
   listOf("tornio","mandrino").any{v.contains(it)} -> "⚙"
   listOf("trapano","fresa","utensil","martello").any{v.contains(it)} -> "🛠"
   listOf("macchin","attrezzatur").any{v.contains(it)} -> "🔧"
   else -> "📦"
  }
 }
 fun imageFor(name:String,category:String,uri:String,size:Int):View {
  if(uri.isNotBlank())try {
   return ImageView(this).apply {
    setImageURI(Uri.parse(uri));scaleType=ImageView.ScaleType.CENTER_CROP
    layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
   }
  }catch(_:Exception){}
  return TextView(this).apply {
   text=iconFor(name,category);textSize=34f;gravity=Gravity.CENTER
   setBackgroundColor(0xffeee8df.toInt())
   layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
  }
 }
 fun categoryGrid(){
  val cats=db.cats();val counts=db.items().groupingBy{it.category}.eachCount()
  cats.chunked(3).forEach{group->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   group.forEach{cat->
    val tile=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(dp(3),dp(8),dp(3),dp(8));setBackgroundColor(0xffffffff.toInt())}
    tile.addView(TextView(this).apply{text=iconFor(cat,cat);textSize=32f;gravity=Gravity.CENTER})
    tile.addView(t(cat,12f).apply{gravity=Gravity.CENTER;maxLines=2})
    tile.addView(t("${counts[cat]?:0} articoli",11f).apply{gravity=Gravity.CENTER})
    tile.setOnClickListener{items(category=cat)}
    row.addView(tile,LinearLayout.LayoutParams(0,dp(130),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
   }
   repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))}
   box.addView(row)
  }
 }
 fun home(){
  page("Inventario Caste")
  val all=db.items()
  box.addView(t("Il tuo laboratorio, sempre sotto controllo.",18f))
  box.addView(t("Articoli: ${all.size}     Scorte basse: ${all.count{it.min>0&&it.qty<=it.min}}",17f))
  box.addView(b("＋ Aggiungi articolo"){editor(null)})
  box.addView(b("▤ Tutti gli articoli"){items()})
  box.addView(b("▦ Gestisci categorie"){categories()})
  box.addView(b("€ Storico acquisti"){purchases()})
  box.addView(b("⚠ Scorte da reintegrare"){items(true)})
  box.addView(b("⇧ Esporta inventario CSV"){export()})
  box.addView(t("Apri una categoria",18f));categoryGrid()
 }
 fun items(low:Boolean=false,category:String?=null){
  page(if(category!=null)category else if(low)"Scorte basse" else "Tutti gli articoli")
  val search=EditText(this).apply{hint="Cerca nome, categoria o posizione"};box.addView(search)
  val list=LinearLayout(this).apply{orientation=1};box.addView(list)
  fun refresh(){
   list.removeAllViews()
   val found=db.items(search.text.toString()).filter{(category==null||it.category==category)&&(!low||(it.min>0&&it.qty<=it.min))}
   if(found.isEmpty())list.addView(t("Nessun articolo trovato"))
   found.forEach{i->
    val card=LinearLayout(this).apply{orientation=1;setPadding(dp(8),dp(8),dp(8),dp(8));setBackgroundColor(0xffffffff.toInt())}
    val main=LinearLayout(this).apply{orientation=0;gravity=Gravity.CENTER_VERTICAL}
    main.addView(imageFor(i.name,i.category,i.photo,72))
    val details=LinearLayout(this).apply{orientation=1;setPadding(dp(10),0,0,0)}
    details.addView(t(i.name,17f).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
    details.addView(t("Quantità: ${i.qty} ${i.unit}"+if(i.min>0&&i.qty<=i.min)"  ⚠ SCORTA BASSA" else "",13f))
    details.addView(t("Posizione: ${i.location.ifBlank { "Non indicata" }}",12f))
    details.addView(t("Costo unitario: €${i.price}",12f))
    main.addView(details,LinearLayout.LayoutParams(0,-2,1f));card.addView(main)
    val quick=LinearLayout(this).apply{orientation=0}
    quick.addView(b("− 1"){db.adjust(i,-1.0);refresh()},LinearLayout.LayoutParams(0,-2,1f))
    quick.addView(b("＋ 1"){db.adjust(i,1.0);refresh()},LinearLayout.LayoutParams(0,-2,1f));card.addView(quick)
    val actions=LinearLayout(this).apply{orientation=0}
    actions.addView(b("Modifica"){editor(i)},LinearLayout.LayoutParams(0,-2,1f))
    actions.addView(b("Acquisto"){buyDialog(i)},LinearLayout.LayoutParams(0,-2,1f))
    actions.addView(b("Elimina"){AlertDialog.Builder(this@MainActivity).setMessage("Eliminare ${i.name}?").setNegativeButton("No",null).setPositiveButton("Sì"){_,_->db.delete(i.id);refresh()}.show()},LinearLayout.LayoutParams(0,-2,1f))
    card.addView(actions)
    list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(5),0,dp(7))})
   }
  }
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
   override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){refresh()}
   override fun afterTextChanged(e:android.text.Editable?){}
  })
  refresh();box.addView(b("＋ Aggiungi articolo"){editor(null)});box.addView(b("← Home"){home()})
 }
 fun editor(i:Item?){editId=i?.id?:0L;photo=i?.photo?:"";page(if(i==null)"Nuovo articolo" else "Modifica articolo")
  fun field(label:String,value:String="",num:Boolean=false):EditText{box.addView(t(label));return EditText(this).apply{setText(value);hint=label;if(num)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}.also{box.addView(it)}}
  val name=field("Nome articolo",i?.name?:"");box.addView(t("Categoria"));val cats=db.cats();val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,cats);sp.setSelection((cats.indexOf(i?.category)).coerceAtLeast(0));box.addView(sp)
  val qty=field("Quantità",i?.qty?.toString()?: "1",true);val unit=field("Unità (pz, m, kg, confezioni)",i?.unit?:"pz");val loc=field("Posizione precisa",i?.location?:"");val min=field("Scorta minima",i?.min?.toString()?:"0",true);val price=field("Costo unitario (€)",i?.price?.toString()?:"0",true);val notes=field("Note",i?.notes?:"")
  box.addView(t(if(photo.isBlank())"Nessuna foto selezionata" else "Foto associata"));box.addView(b("📷 Scegli foto"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},41)})
  box.addView(b("Salva articolo"){if(name.text.isBlank()){name.error="Inserisci il nome";return@b};db.save(Item(editId,name.text.toString(),sp.selectedItem.toString(),qty.text.toString().toDoubleOrNull()?:0.0,unit.text.toString(),loc.text.toString(),min.text.toString().toDoubleOrNull()?:0.0,price.text.toString().toDoubleOrNull()?:0.0,photo,notes.text.toString()));items()});box.addView(b("Annulla"){home()})}
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==41&&c==RESULT_OK){d?.data?.let{u->try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};photo=u.toString();Toast.makeText(this,"Foto associata",Toast.LENGTH_SHORT).show()}};if(r==42&&c==RESULT_OK)d?.data?.let{contentResolver.openOutputStream(it)?.bufferedWriter()?.use{w->w.write(csv)};Toast.makeText(this,"CSV esportato",Toast.LENGTH_SHORT).show()}}
 fun categories(){page("Categorie");categoryGrid();box.addView(b("＋ Nuova categoria"){val e=EditText(this).apply{hint="Nome categoria"};AlertDialog.Builder(this).setTitle("Nuova categoria").setView(e).setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->if(e.text.isNotBlank()){db.addCat(e.text.toString());categories()}}.show()});box.addView(b("← Home"){home()})}
 fun buyDialog(i:Item){val l=LinearLayout(this).apply{orientation=1;setPadding(dp(16),0,dp(16),0)};val date=EditText(this).apply{setText(SimpleDateFormat("yyyy-MM-dd",Locale.ITALY).format(Date()))};val q=EditText(this).apply{hint="Quantità";inputType=2};val total=EditText(this).apply{hint="Totale €";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};val supplier=EditText(this).apply{hint="Fornitore"};listOf(date,q,total,supplier).forEach{l.addView(it)};AlertDialog.Builder(this).setTitle("Registra acquisto").setView(l).setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->db.buy(i,date.text.toString(),q.text.toString().toDoubleOrNull()?:0.0,total.text.toString().toDoubleOrNull()?:0.0,supplier.text.toString());items()}.show()}
 fun purchases(){page("Storico acquisti");val a=db.buys();if(a.isEmpty())box.addView(t("Nessun acquisto registrato"));a.forEach{box.addView(t(it))};box.addView(b("← Home"){home()})}
 fun export(){csv=buildString{append("Nome;Categoria;Quantita;Unita;Posizione;ScortaMinima;CostoUnitario;Note\n");db.items().forEach{append("${it.name};${it.category};${it.qty};${it.unit};${it.location};${it.min};${it.price};${it.notes}\n")}};startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/csv";putExtra(Intent.EXTRA_TITLE,"inventario_caste.csv")},42)}
}
