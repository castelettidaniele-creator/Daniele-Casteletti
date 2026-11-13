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
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import java.io.ByteArrayOutputStream

data class Item(val id:Long=0,val name:String="",val category:String="",val qty:Double=0.0,
 val unit:String="pz",val location:String="",val min:Double=0.0,val price:Double=0.0,
 val photo:String="",val notes:String="")

class DB(c:Context):SQLiteOpenHelper(c,"caste.db",null,2) {
 private val bike = listOf("MTB","Da strada","Da passeggio uomo","Da passeggio donna","Bambino")
 override fun onCreate(d:SQLiteDatabase) {
  d.execSQL("CREATE TABLE items(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT,category TEXT,qty REAL,unit TEXT,location TEXT,min REAL,price REAL,photo TEXT,notes TEXT)")
  d.execSQL("CREATE TABLE cats(name TEXT PRIMARY KEY)")
  d.execSQL("CREATE TABLE buys(id INTEGER PRIMARY KEY AUTOINCREMENT,item TEXT,date TEXT,qty REAL,total REAL,supplier TEXT)")
  d.execSQL("CREATE TABLE item_categories(item_id INTEGER NOT NULL,category TEXT NOT NULL,PRIMARY KEY(item_id,category))")
  initialize(d)
 }
 override fun onUpgrade(d:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
  if(oldVersion<2){
   d.beginTransaction()
   try {
    d.execSQL("CREATE TABLE IF NOT EXISTS item_categories(item_id INTEGER NOT NULL,category TEXT NOT NULL,PRIMARY KEY(item_id,category))")
    d.execSQL("UPDATE items SET category='Fresa CNC' WHERE category='Macchinari'")
    // Categoria rimossa: gli articoli restano conservati in Utensili.
    d.execSQL("UPDATE items SET category='Utensili' WHERE category='Attrezzatura e accessori'")
    d.execSQL("INSERT OR IGNORE INTO item_categories(item_id,category) SELECT id,category FROM items")
    initialize(d)
    d.setTransactionSuccessful()
   } finally {d.endTransaction()}
  }
 }
 private fun initialize(d:SQLiteDatabase){
  listOf("Fresa CNC","Tornio","Stampa 3D","Utensili","Legname e materiali","Minuteria","Materiale elettrico","Materiale di consumo","Vernici","Bicicletta").forEach{d.execSQL("INSERT OR IGNORE INTO cats(name) VALUES(?)",arrayOf(it))}
  bike.forEach{d.execSQL("INSERT OR IGNORE INTO cats(name) VALUES(?)",arrayOf("Bicicletta / $it"))}
  d.execSQL("DELETE FROM cats WHERE name IN ('Macchinari','Attrezzatura e accessori')")
 }
 override fun onOpen(d:SQLiteDatabase){super.onOpen(d);if(!d.isReadOnly)initialize(d)}
 fun cats():List<String>{val a=mutableListOf<String>();readableDatabase.rawQuery("SELECT name FROM cats ORDER BY name",null).use{while(it.moveToNext())a.add(it.getString(0))};return a}
 fun addCat(s:String){writableDatabase.execSQL("INSERT OR IGNORE INTO cats(name) VALUES(?)",arrayOf(s.trim()))}
 fun items(q:String=""):List<Item>{
  val a=mutableListOf<Item>()
  readableDatabase.rawQuery("SELECT id,name,category,qty,unit,location,min,price,photo,notes FROM items WHERE name LIKE ? OR category LIKE ? OR EXISTS(SELECT 1 FROM item_categories ic WHERE ic.item_id=items.id AND ic.category LIKE ?) ORDER BY name",arrayOf("%$q%","%$q%","%$q%")).use{c->while(c.moveToNext())a.add(Item(c.getLong(0),c.getString(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getDouble(6),c.getDouble(7),c.getString(8),c.getString(9)))}
  return a
 }
 fun assignments(id:Long):Set<String>{val a=mutableSetOf<String>();readableDatabase.rawQuery("SELECT category FROM item_categories WHERE item_id=?",arrayOf(id.toString())).use{while(it.moveToNext())a.add(it.getString(0))};return a}
 fun belongs(id:Long,category:String)=assignments(id).contains(category)
 fun save(i:Item,selected:Set<String>?=null){
  val d=writableDatabase;d.beginTransaction()
  try{
   val v=arrayOf(i.name,i.category,i.qty.toString(),i.unit,i.location,i.min.toString(),i.price.toString(),i.photo,i.notes)
   var id=i.id
   if(id==0L){d.execSQL("INSERT INTO items(name,category,qty,unit,location,min,price,photo,notes) VALUES(?,?,?,?,?,?,?,?,?)",v);id=android.database.DatabaseUtils.longForQuery(d,"SELECT last_insert_rowid()",null)}
   else d.execSQL("UPDATE items SET name=?,category=?,qty=?,unit=?,location=?,min=?,price=?,photo=?,notes=? WHERE id=?",v+arrayOf(id.toString()))
   if(selected!=null){d.delete("item_categories","item_id=?",arrayOf(id.toString()));(selected+i.category).forEach{d.execSQL("INSERT OR IGNORE INTO item_categories(item_id,category) VALUES(?,?)",arrayOf(id.toString(),it))}}
   d.setTransactionSuccessful()
  }finally{d.endTransaction()}
 }
 fun adjust(i:Item,n:Double){save(i.copy(qty=(i.qty+n).coerceAtLeast(0.0)))}
 fun delete(id:Long){val d=writableDatabase;d.beginTransaction();try{d.delete("item_categories","item_id=?",arrayOf(id.toString()));d.delete("items","id=?",arrayOf(id.toString()));d.setTransactionSuccessful()}finally{d.endTransaction()}}
}
class MainActivity:Activity(){
 lateinit var db:DB; lateinit var box:LinearLayout; var photo=""; var editId=0L; var csv=""; var scanPhoto:Uri?=null
 fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
 fun t(s:String,size:Float=16f)=TextView(this).apply{text=s;textSize=size;setTextColor(0xff302820.toInt());setPadding(4,dp(7),4,dp(7))}
 fun b(s:String,f:()->Unit)=Button(this).apply{text=s;setOnClickListener{f()}}
  fun page(title:String){
   val root=LinearLayout(this).apply{orientation=1;setBackgroundColor(0xfff7f3ef.toInt())}
   val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setBackgroundColor(0xff5d4037.toInt());setPadding(dp(14),dp(10),dp(8),dp(10))}
   header.addView(TextView(this).apply{text=title;textSize=23f;setTextColor(-1);setTypeface(null,android.graphics.Typeface.BOLD)},LinearLayout.LayoutParams(0,-2,1f))
   val menu=TextView(this).apply{text="☰";textSize=28f;setTextColor(-1);gravity=Gravity.CENTER;setPadding(dp(12),dp(6),dp(12),dp(6))}
   menu.setOnClickListener{v->
    val pop=PopupMenu(this,v)
    listOf("Home","Aggiungi articolo","Tutti gli articoli","Gestisci categorie","Esporta inventario CSV","Fotografa e conta").forEachIndexed{index,label->pop.menu.add(0,index, index,label)}
    pop.setOnMenuItemClickListener{item->when(item.itemId){0->home();1->editor(null);2->items();3->categories();4->export();5->photoCount()};true}
    pop.show()
   }
   header.addView(menu);root.addView(header)
   val sc=ScrollView(this);box=LinearLayout(this).apply{orientation=1;setPadding(dp(10),dp(10),dp(10),dp(20))};sc.addView(box)
   root.addView(sc,LinearLayout.LayoutParams(-1,0,1f))
   root.setOnApplyWindowInsetsListener{v,w->v.setPadding(0,w.systemWindowInsetTop,0,w.systemWindowInsetBottom);w}
   setContentView(root)
  }
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
 fun productPhotoId(name:String, category:String):Int {
  val n=name.lowercase(Locale.ROOT)
  // Il nome specifico ha precedenza sulla categoria generica.
  val key=when {
   listOf("cuscinet", "bearing").any{n.contains(it)} -> "prod_cuscinetto"
   listOf("rondell", "washer").any{n.contains(it)} -> "prod_rondella"
   listOf("dado", "bullon", "nut ").any{n.contains(it)} -> "prod_dado"
   listOf("vite", "viti", "screw").any{n.contains(it)} -> "prod_vite"
   listOf("fascett", "zip tie").any{n.contains(it)} -> "prod_fascetta"
   listOf("connettor", "morsett", "spinott").any{n.contains(it)} -> "prod_connettore"
   listOf("striscia led", "strip led", "led").any{n.contains(it)} -> "prod_led"
   listOf("cavo", "filo elettric", "wire").any{n.contains(it)} -> "prod_cavo"
   listOf("filament", "bobina", "pla", "petg", "abs").any{n.contains(it)} -> "prod_filamento"
   listOf("stampante 3d", "printer 3d").any{n.contains(it)} -> "prod_stampante3d"
   listOf("vernice", "smalto", "impregnant", "pittura", "lacca").any{n.contains(it)} -> "prod_vernice"
   listOf("colla", "adesiv").any{n.contains(it)} -> "prod_colla"
   listOf("carta abrasiv", "disco abrasiv", "abrasiv", "levig").any{n.contains(it)} -> "prod_abrasivo"
   listOf("trapano", "avvitatore").any{n.contains(it)} -> "prod_trapano"
   listOf("sega", "circolare").any{n.contains(it)} -> "prod_sega"
   listOf("tavola", "asse", "pino", "listello", "legno", "multistrato").any{n.contains(it)} -> "prod_legno"
   listOf("tornio", "mandrino").any{n.contains(it)} -> "prod_tornio"
   listOf("scalpello", "martello", "fresa", "chiave", "utensil").any{n.contains(it)} -> "prod_utensile"
   else -> when(category.lowercase(Locale.ROOT)) {
    "minuteria" -> "prod_vite"
    "materiale elettrico" -> "prod_cavo"
    "legname e materiali" -> "prod_legno"
    "vernici" -> "prod_vernice"
    "stampa 3d" -> "prod_stampante3d"
    "tornio" -> "prod_tornio"
    "utensili" -> "prod_utensile"
    "materiale di consumo" -> "prod_abrasivo"
    "fresa cnc" -> "prod_macchinario"
    "bicicletta" -> "prod_utensile"
    else -> ""
   }
  }
  return if(key.isNotEmpty())resources.getIdentifier(key,"drawable",packageName) else 0
 }
 fun imageFor(name:String,category:String,uri:String,size:Int):View {
  val photoView=ImageView(this).apply {
   scaleType=ImageView.ScaleType.FIT_CENTER
   setBackgroundColor(0xffffffff.toInt())
   layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
  }
  if(uri.isNotBlank()) {
   try {
    // Verifica accesso effettivo: un URI non leggibile non nasconde la foto automatica.
    contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
     val bitmap=android.graphics.BitmapFactory.decodeStream(stream)
     if(bitmap!=null){photoView.setImageBitmap(bitmap);return photoView}
    }
   }catch(_:Exception){}
  }
  val id=productPhotoId(name,category)
  if(id!=0){photoView.setImageResource(id);return photoView}
  return TextView(this).apply {
   text=iconFor(name,category);textSize=34f;gravity=Gravity.CENTER
   setBackgroundColor(0xffeee8df.toInt())
   layoutParams=LinearLayout.LayoutParams(dp(size),dp(size))
  }
 }
  fun categoryPhoto(cat:String):View {
   val key=when(cat.lowercase(Locale.ROOT)){
    "bicicletta"->"cat_bicicletta"
    "bicicletta / mtb"->"cat_bici_mtb"
    "bicicletta / da strada"->"cat_bici_strada"
    "bicicletta / da passeggio uomo"->"cat_bici_uomo"
    "bicicletta / da passeggio donna"->"cat_bici_donna"
    "bicicletta / bambino"->"cat_bici_bambino"
    "legname e materiali"->"cat_legname"
    "fresa cnc"->"cat_macchinari"
    "materiale di consumo"->"cat_consumo"
    "materiale elettrico"->"cat_elettrico"
    "minuteria"->"cat_minuteria"
    "stampa 3d"->"cat_stampa3d"
    "tornio"->"cat_tornio"
    "utensili"->"cat_utensili"
    "vernici"->"cat_vernici"
    else->""
   }
   val id=if(key.isNotEmpty())resources.getIdentifier(key,"drawable",packageName) else 0
   if(id!=0)return ImageView(this).apply{setImageResource(id);scaleType=ImageView.ScaleType.FIT_CENTER;layoutParams=LinearLayout.LayoutParams(-1,dp(83))}
   return TextView(this).apply{text=iconFor(cat,cat);textSize=32f;gravity=Gravity.CENTER;layoutParams=LinearLayout.LayoutParams(-1,dp(83))}
  }
  fun categoryGrid(){
   val cats=db.cats().filter{!it.startsWith("Bicicletta /")};val all=db.items();val counts=cats.associateWith{cat->if(cat=="Bicicletta")all.count{i->db.assignments(i.id).any{it.startsWith("Bicicletta /")||it=="Bicicletta"}} else all.count{db.belongs(it.id,cat)}}
   cats.chunked(3).forEach{group->
    val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
    group.forEach{cat->
     val tile=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(dp(2),dp(5),dp(2),dp(5));setBackgroundColor(-1)}
     tile.addView(categoryPhoto(cat))
     tile.addView(t(cat,12f).apply{gravity=Gravity.CENTER;maxLines=2})
     tile.addView(t("${counts[cat]?:0} articoli",11f).apply{gravity=Gravity.CENTER})
     tile.setOnClickListener{if(cat=="Bicicletta")bikeFolders() else items(category=cat)}
     row.addView(tile,LinearLayout.LayoutParams(0,dp(180),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
    }
    repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))}
    box.addView(row)
   }
  }
  fun home(){
   page("Inventario")
   val all=db.items()
   box.addView(t("Articoli: ${all.size}",17f).apply{setTypeface(null,android.graphics.Typeface.BOLD)})
   box.addView(t("Apri una categoria",18f))
   categoryGrid()
  }
 fun bikeFolders(){
  page("Bicicletta")
  box.addView(t("Scegli una sottocartella",18f))
  val folders=listOf("MTB","Da strada","Da passeggio uomo","Da passeggio donna","Bambino")
  val all=db.items()
  folders.chunked(3).forEach { group ->
   val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
   group.forEach { folder ->
    val category="Bicicletta / $folder"
    val tile=LinearLayout(this).apply{orientation=1;gravity=Gravity.CENTER;setPadding(dp(2),dp(5),dp(2),dp(5));setBackgroundColor(-1)}
    tile.addView(categoryPhoto(category))
    tile.addView(t(folder,12f).apply{gravity=Gravity.CENTER;maxLines=2})
    val count=all.count{db.belongs(it.id,category)}
    tile.addView(t("$count articoli",11f).apply{gravity=Gravity.CENTER})
    tile.setOnClickListener{items(category=category)}
    row.addView(tile,LinearLayout.LayoutParams(0,dp(180),1f).apply{setMargins(dp(2),dp(2),dp(2),dp(2))})
   }
   repeat(3-group.size){row.addView(View(this),LinearLayout.LayoutParams(0,1,1f))}
   box.addView(row)
  }
  box.addView(b("← Home"){home()})
 }
 fun smallAction(symbol:String,description:String,action:()->Unit):View = TextView(this).apply{
  text=symbol;textSize=19f;gravity=Gravity.CENTER;contentDescription=description
  setPadding(dp(8),dp(8),dp(8),dp(8));minWidth=dp(44);minHeight=dp(44)
  setOnClickListener{action()}
 }
 fun items(category:String?=null){
  page(category?.removePrefix("Bicicletta / ")?:"Tutti gli articoli")
  val search=EditText(this).apply{hint="Cerca nome o categoria"};box.addView(search)
  val list=LinearLayout(this).apply{orientation=1};box.addView(list)
  fun refresh(){
   list.removeAllViews()
   val found=db.items(search.text.toString()).filter{category==null||db.belongs(it.id,category)}
   if(found.isEmpty())list.addView(t("Nessun articolo trovato"))
   found.forEach{i->
    val card=LinearLayout(this).apply{orientation=1;setPadding(dp(7),dp(5),dp(7),dp(5));setBackgroundColor(-1)}
    val main=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    main.addView(imageFor(i.name,i.category,i.photo,68))
    val details=LinearLayout(this).apply{orientation=1;setPadding(dp(8),0,0,0)}
    val top=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    top.addView(t(i.name,16f).apply{setTypeface(null,android.graphics.Typeface.BOLD);setPadding(0,0,0,0)},LinearLayout.LayoutParams(0,-2,1f))
    top.addView(smallAction("✏","Modifica ${i.name}"){editor(i)})
    top.addView(smallAction("🗑","Elimina ${i.name}"){AlertDialog.Builder(this@MainActivity).setMessage("Eliminare ${i.name}?").setNegativeButton("No",null).setPositiveButton("Sì"){_,_->db.delete(i.id);refresh()}.show()})
    details.addView(top)
    details.addView(t("Quantità: ${i.qty} ${i.unit}",13f).apply{setPadding(0,0,0,0)})
    details.addView(t("Costo unitario: €${i.price}",12f).apply{setPadding(0,0,0,0)})
    val quick=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.END}
    quick.addView(smallAction("−1","Diminuisci quantità"){db.adjust(i,-1.0);refresh()})
    quick.addView(smallAction("+1","Aumenta quantità"){db.adjust(i,1.0);refresh()})
    details.addView(quick)
    main.addView(details,LinearLayout.LayoutParams(0,-2,1f));card.addView(main)
    list.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,dp(4),0,dp(4))})
   }
  }
  search.addTextChangedListener(object:android.text.TextWatcher{
   override fun beforeTextChanged(s:CharSequence?,st:Int,c:Int,a:Int){}
   override fun onTextChanged(s:CharSequence?,st:Int,b:Int,c:Int){refresh()}
   override fun afterTextChanged(e:android.text.Editable?){}
  })
  refresh();box.addView(b("＋ Aggiungi articolo"){editor(null,category)});box.addView(b("← Home"){home()})
 }
 fun editor(i:Item?,initialCategory:String?=null){
  editId=i?.id?:0L;photo=i?.photo?:"";page(if(i==null)"Nuovo articolo" else "Modifica articolo")
  fun field(label:String,value:String="",num:Boolean=false):EditText{
   box.addView(t(label));return EditText(this).apply{setText(value);hint=label;if(num)inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL}.also{box.addView(it)}
  }
  val name=field("Nome articolo",i?.name?:"")
  box.addView(t("Categorie (puoi selezionarne più di una)",16f))
  val chosen=mutableSetOf<String>()
  if(i!=null)chosen.addAll(db.assignments(i.id))
  else if(initialCategory!=null)chosen.add(initialCategory)
  val checks=mutableMapOf<String,CheckBox>()
  db.cats().filter{it!="Bicicletta"}.forEach{cat->
   val display=if(cat.startsWith("Bicicletta /"))"🚲 ${cat.removePrefix("Bicicletta / ")}" else cat
   val check=CheckBox(this).apply{text=display;isChecked=chosen.contains(cat)}
   checks[cat]=check;box.addView(check)
  }
  val qty=field("Quantità",i?.qty?.toString()?:"1",true)
  val unit=field("Unità (pz, m, kg, confezioni)",i?.unit?:"pz")
  val price=field("Costo unitario (€)",i?.price?.toString()?:"0",true)
  val notes=field("Note",i?.notes?:"")
  box.addView(t(if(photo.isBlank())"Nessuna foto selezionata" else "Foto associata"))
  box.addView(b("📷 Scegli foto"){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},41)})
  box.addView(b("Salva articolo"){
   if(name.text.isBlank()){name.error="Inserisci il nome";return@b}
   val selected=checks.filterValues{it.isChecked}.keys.toSet()
   if(selected.isEmpty()){Toast.makeText(this,"Seleziona almeno una categoria",Toast.LENGTH_LONG).show();return@b}
   val primary=if(i!=null&&selected.contains(i.category))i.category else selected.sorted().first()
   db.save(Item(editId,name.text.toString(),primary,qty.text.toString().toDoubleOrNull()?:0.0,unit.text.toString(),i?.location?:"",i?.min?:0.0,price.text.toString().toDoubleOrNull()?:0.0,photo,notes.text.toString()),selected)
   items()
  })
  box.addView(b("Annulla"){home()})
 }
 override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==41&&c==RESULT_OK){d?.data?.let{u->try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION)}catch(_:Exception){};photo=u.toString();Toast.makeText(this,"Foto associata",Toast.LENGTH_SHORT).show()}};if(r==43&&c==RESULT_OK){d?.data?.let{scanPhoto=it;photoCount()}};if(r==42&&c==RESULT_OK)d?.data?.let{contentResolver.openOutputStream(it)?.bufferedWriter()?.use{w->w.write(csv)};Toast.makeText(this,"CSV esportato",Toast.LENGTH_SHORT).show()}}

 data class ScanResult(val name:String,val qty:Int,val category:String)
 fun photoCount(){
  page("Fotografa e conta")
  box.addView(t("Gemini suggerisce nomi, quantità e categorie. Controlla sempre il conteggio: nessun articolo sarà salvato senza conferma."))
  val prefs=getSharedPreferences("gemini_config",MODE_PRIVATE)
  val key=EditText(this).apply{hint="Chiave API Gemini (Google AI Studio)";setText(prefs.getString("key", ""));inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
  box.addView(t("Chiave API Gemini gratuita (necessaria solo per l'analisi)"));box.addView(key)
  box.addView(b("Salva chiave sul telefono"){prefs.edit().putString("key",key.text.toString().trim()).apply();Toast.makeText(this,"Chiave memorizzata localmente",Toast.LENGTH_SHORT).show()})
  box.addView(b("📷 Scegli o scatta una fotografia"){
   startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},43)
  })
  if(scanPhoto!=null){
   val preview=ImageView(this).apply{setImageURI(scanPhoto);adjustViewBounds=true;maxHeight=dp(260)}
   box.addView(preview)
   val status=t("Pronto per analizzare")
   box.addView(status)
   box.addView(b("Analizza con Gemini"){
    val apiKey=key.text.toString().trim()
    if(apiKey.isBlank()){key.error="Inserisci la chiave API";return@b}
    prefs.edit().putString("key",apiKey).apply()
    val uri=scanPhoto?:return@b
    status.text="Analisi in corso…"
    Thread{
     try{
      val bitmap=contentResolver.openInputStream(uri)?.use{BitmapFactory.decodeStream(it)}?:error("Impossibile leggere la foto")
      val maxSide=1280f;val scale=minOf(1f,maxSide/maxOf(bitmap.width,bitmap.height))
      val resized=if(scale<1f)Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true) else bitmap
      val bytes=ByteArrayOutputStream().also{resized.compress(Bitmap.CompressFormat.JPEG,80,it)}.toByteArray()
      val encoded=Base64.encodeToString(bytes,Base64.NO_WRAP)
      val cats=db.cats().filter{it!="Bicicletta"}.joinToString(", ")
      val prompt="Analizza la foto di articoli di un laboratorio. Identifica gruppi di oggetti diversi e prova a contare solo quelli visibili. Non inventare misure o caratteristiche non verificabili. Restituisci SOLO JSON valido con struttura {\"items\":[{\"name\":\"nome italiano\",\"qty\":1,\"category\":\"Minuteria\"}]}. Categorie disponibili: $cats. Se il conteggio è incerto, proponi una stima che l'utente controllerà."
      val body=JSONObject().put("contents",JSONArray().put(JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt)).put(JSONObject().put("inline_data",JSONObject().put("mime_type","image/jpeg").put("data",encoded)))))).put("generationConfig",JSONObject().put("responseMimeType","application/json"))
      val conn=(URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent").openConnection() as HttpsURLConnection)
      conn.requestMethod="POST";conn.connectTimeout=20000;conn.readTimeout=60000;conn.doOutput=true
      conn.setRequestProperty("Content-Type","application/json");conn.setRequestProperty("x-goog-api-key",apiKey)
      conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
      val code=conn.responseCode
      val response=(if(code in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
      if(code !in 200..299)error("Gemini HTTP $code: "+response.take(240))
      val raw=JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
      val arr=JSONObject(raw).getJSONArray("items")
      val results=(0 until arr.length()).map{val x=arr.getJSONObject(it);ScanResult(x.optString("name","Articolo"),x.optInt("qty",0).coerceAtLeast(0),x.optString("category","Minuteria"))}
      runOnUiThread{showScanResults(results)}
     }catch(e:Exception){runOnUiThread{status.text="Analisi non riuscita: ${e.message}. Puoi riprovare oppure inserire gli articoli manualmente."}}
    }.start()
   })
  }
  box.addView(b("Inserimento manuale"){showScanResults(listOf(ScanResult("",1,"Minuteria")))})
 }
 fun showScanResults(results:List<ScanResult>){
  page("Controlla risultati")
  box.addView(t("Correggi tutti i campi prima di confermare. I conteggi Gemini sono stime."))
  val entries=mutableListOf<Triple<EditText,EditText,Spinner>>()
  val cats=db.cats().filter{it!="Bicicletta"}
  fun addEntry(r:ScanResult){
   val name=EditText(this).apply{hint="Nome articolo";setText(r.name)}
   val qty=EditText(this).apply{hint="Quantità";inputType=InputType.TYPE_CLASS_NUMBER;setText(r.qty.toString())}
   val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,cats);setSelection(cats.indexOf(r.category).coerceAtLeast(0))}
   box.addView(t("Articolo ${entries.size+1}"));box.addView(name);box.addView(qty);box.addView(cat)
   entries.add(Triple(name,qty,cat))
  }
  results.forEach{addEntry(it)}
  box.addView(b("＋ Aggiungi altro oggetto"){addEntry(ScanResult("",1,"Minuteria"))})
  box.addView(b("Conferma e salva"){
   val parsed=entries.map{(name,qty,cat)->Triple(name.text.toString().trim(),qty.text.toString().toIntOrNull(),cat.selectedItem?.toString().orEmpty())}
   if(parsed.any{it.first.isBlank()||it.second==null||it.second!!<0||it.third.isBlank()}){Toast.makeText(this,"Correggi nomi, quantità e categorie",Toast.LENGTH_LONG).show();return@b}
   AlertDialog.Builder(this).setTitle("Conferma salvataggio").setMessage("Salvare ${parsed.size} articoli? Gli articoli con lo stesso nome e categoria saranno aggiunti alle scorte esistenti.")
    .setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->
     parsed.forEach{(name,count,cat)->
      val existing=db.items().firstOrNull{it.name.equals(name,true)&&db.belongs(it.id,cat)}
      if(existing!=null)db.adjust(existing,count!!.toDouble()) else db.save(Item(name=name,category=cat,qty=count!!.toDouble()),setOf(cat))
     }
     Toast.makeText(this,"Articoli salvati",Toast.LENGTH_SHORT).show();items()
    }.show()
  })
  box.addView(b("Annulla senza salvare"){home()})
 }
 fun categories(){page("Categorie");categoryGrid();box.addView(b("＋ Nuova categoria"){val e=EditText(this).apply{hint="Nome categoria"};AlertDialog.Builder(this).setTitle("Nuova categoria").setView(e).setNegativeButton("Annulla",null).setPositiveButton("Salva"){_,_->if(e.text.isNotBlank()){db.addCat(e.text.toString());categories()}}.show()});box.addView(b("← Home"){home()})}
 fun export(){
  csv=buildString{
   append("Nome;Categorie;Quantita;Unita;CostoUnitario;Note\n")
   db.items().forEach{i->append("${i.name};${db.assignments(i.id).sorted().joinToString(", ")};${i.qty};${i.unit};${i.price};${i.notes}\n")}
  }
  startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/csv";putExtra(Intent.EXTRA_TITLE,"inventario.csv")},42)
 }
}
