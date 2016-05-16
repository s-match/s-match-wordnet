package it.unitn.disi.smatch.oracles.uby.test.experimental;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.dom4j.io.SAXReader;
import org.hibernate.SQLQuery;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistryBuilder;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import de.tudarmstadt.ukp.lmf.api.Uby;
import de.tudarmstadt.ukp.lmf.hibernate.HibernateConnect;
import de.tudarmstadt.ukp.lmf.hibernate.UBYH2Dialect;
import de.tudarmstadt.ukp.lmf.model.semantics.SynsetRelation;
import de.tudarmstadt.ukp.lmf.transform.DBConfig;
import de.tudarmstadt.ukp.lmf.transform.UBYHibernateTransformer;
import de.tudarmstadt.ukp.lmf.transform.XMLToDBTransformer;

/**
 * Tests to check we can extend lmf model with extra attributes we need
 * 
 * @since 0.1
 */
public class TestExtendingHibernate {

	private static final Logger log = LoggerFactory.getLogger(TestExtendingHibernate.class);

	private static Map<DBConfig, Configuration> cachedHibernateConfigurations = new HashMap();
	
	/**
	 * Mappings from Uby classes to out own custom ones. 
	 */
	private static LinkedHashMap<String,String> customClassMappings;

	{
		customClassMappings = new LinkedHashMap();
		customClassMappings.put("de.tudarmstadt.ukp.lmf.model.semantics.SynsetRelation",
								 "it.unitn.disi.smatch.oracles.uby.test.experimental.MySynsetRelation");
	}
	
	
	private DBConfig dbConfig;
	
	private ExtendedUby uby;
	
	@Before
	public void beforeMethod(){
		 dbConfig = new DBConfig("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "org.h2.Driver",
				UBYH2Dialect.class.getName(), "root", "pass", true);

	}
	
	@After
	public void afterMethod(){
		dbConfig = null;
	}
	
	private static class ExtendedUby extends Uby {		
		
		
		
		public ExtendedUby(DBConfig dbConfig) {
			super(dbConfig);		
			
			if (dbConfig == null) {
				throw new IllegalArgumentException("database configuration is null");
			}
			this.dbConfig = dbConfig;
			
			// dav: note here we are overwriting cfg and SessionFactory					
			cfg = getConfiguration(dbConfig);

			ServiceRegistryBuilder serviceRegistryBuilder = new ServiceRegistryBuilder()
					.applySettings(cfg.getProperties());
			sessionFactory = cfg.buildSessionFactory(serviceRegistryBuilder.buildServiceRegistry());
			openSession();

		}

	}

	/**
	 * Create all LMF Tables in the database based on the hibernate mapping
	 * 
	 * @param dbConfig
	 * @throws FileNotFoundException
	 */
	public static void createTables(DBConfig dbConfig) throws FileNotFoundException {
	
		log.info("CREATE TABLES");
		
		Configuration hcfg = getConfiguration(dbConfig);
		
		hcfg.setProperty("hibernate.hbm2ddl.auto", "none");
		SchemaExport se = new SchemaExport(hcfg);
		se.create(true, true);
	}
	
	private static void loadHibernateCfg(Configuration hcfg, Resource xml) {
		
		log.info("Loading config " + xml.getDescription() + " ...");
	
		try {
			
			java.util.Scanner sc = new java.util.Scanner(xml.getInputStream()).useDelimiter("\\A");
			String s = sc.hasNext() ? sc.next() : "";
			sc.close();
				
			for (Map.Entry<String, String> e : customClassMappings.entrySet()){
				s = s.replace(e.getKey(), e.getValue());
			}
			hcfg.addXML(s);		
			
		} catch (Exception e) {
			throw new RuntimeException("Error while reading file at path: " + xml.getDescription() , e);
		}
	
	}


	
	private static Configuration getConfiguration(DBConfig dbConfig) {

		if (cachedHibernateConfigurations.get(dbConfig) != null){
			log.debug("Returning cached configuration.");
			return cachedHibernateConfigurations.get(dbConfig);
		}
		
		log.info("Going to load configuration...");
		
		Configuration hcfg = new Configuration()
				.addProperties(HibernateConnect.getProperties(dbConfig.getJdbc_url(), 
						dbConfig.getJdbc_driver_class(),
						dbConfig.getDb_vendor(), 
						dbConfig.getUser(), 
						dbConfig.getPassword(), 
						dbConfig.isShowSQL()));

		// load hibernate mappings
		ClassLoader cl = HibernateConnect.class.getClassLoader();
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);
		Resource[] mappings = null;
		try {
			mappings = resolver.getResources("hibernatemap/access/**/*.hbm.xml");
			for (Resource mapping : mappings) {
				boolean isCustomized = false;
				for (String c : customClassMappings.keySet()){
					String[] cs = c.split("\\.");
					String cn = cs[cs.length-1];
					if (mapping.getFilename().replace(".hbm.xml", "").contains(cn)){
						isCustomized = true;
					}
				}
				if (!isCustomized){	
					loadHibernateCfg(hcfg, mapping);
				}				
			}

		} catch (IOException e) {
			throw new RuntimeException("Error while loading hibernate mappings!", e);
		}

		log.info("Loading custom mappings... ");

		// ClassLoader cl = HibernateConnect.class.getClassLoader();
		// ClassLoader cl = TestHibernateExtension.class.getClassLoader();
		// PathMatchingResourcePatternResolver resolver = new
		// PathMatchingResourcePatternResolver(
		// cl);
		// Resource[] mappings = null;
		try {
							
			Resource[] resources = new PathMatchingResourcePatternResolver(
											TestExtendingHibernate.class.getClassLoader())
									.getResources("hybernatemap/access/**/*.hbm.xml");
			
			assertEquals(1, resources.length);
			
			for (Resource r : resources){
				loadHibernateCfg(hcfg, r );
			}			

		} catch (Exception e) {
			throw new RuntimeException("Hibernate mappings not found!", e);
		}

		log.info("Done loading custom mappings. ");

		cachedHibernateConfigurations.put(dbConfig, hcfg) ;
		return hcfg;
	}

	
	MySynsetRelation loadDb(){

		try {
			createTables(dbConfig);
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Couldn't create tables in database " + dbConfig.getJdbc_url() + "!", e); // todo
																													// what
																													// about
			// DisiRuntimeException?
		}

		log.info("TEST: LOADING AUGMENTED UBY....");
		uby = new ExtendedUby(dbConfig);

		log.info("TEST: LOADING XML....");
		XMLToDBTransformer trans = new XMLToDBTransformer(uby.getDbConfig());

		String filepath = "src/test/resources/it/unitn/disi/smatch/oracles/uby/test/experimental/augmented-synset-relation.xml";
		try {
			trans.transform(new File(filepath), "Test resource name");
		} catch (Exception ex) {
			throw new RuntimeException("Error while loading lmf xml " + filepath, ex);
		}
		List<SynsetRelation> synRels = uby.getSynsetById("synset 1").getSynsetRelations();

		assertEquals(1, synRels.size());

		SynsetRelation rel = synRels.get(0);

		assertNotNull(rel);

		log.info("Asserting rel is instance of " + MySynsetRelation.class);
		if (!(rel instanceof MySynsetRelation)) {
			throw new RuntimeException(
					"relation is not of type " + MySynsetRelation.class + " found instead " + rel.getClass());
		}
		
		 MySynsetRelation myRel = (MySynsetRelation) rel;
		
		// assertEquals (3, ((ExperimentalSynsetRelation) rel).getDepth());
		assertEquals("abc",myRel.getRelName());
		
		return myRel;
		
	};
	
	/**
	 * 
	 *
	 */
	@Test
	public void testHibernate() {
		
		MySynsetRelation myRel = loadDb();
		
		// sic - loading from xml doesn't pick the custom values..
		assertEquals(null, myRel.getMyField()); 
			
		myRel.setMyField("HELLO");
		
		assertTrue(uby.getSession().isDirty());
		
		log.info("saving...");
		uby.getSession().save(myRel);
		log.info("done saving.");
		
		assertTrue(uby.getSession().isDirty());
		
		log.info("flushing...");		
		uby.getSession().flush();
		log.info("done flushing");
		
		assertFalse(uby.getSession().isDirty());
		
		//uby.getSession().close();
				
		
		String sql="SELECT myField FROM SynsetRelation";
		SQLQuery query = uby.getSession().createSQLQuery(sql);
		Iterator<?> iter = query.list().iterator();

		boolean found = false;
		while (iter.hasNext()) {
			String s= (String) iter.next();			
			
			assertEquals("HELLO", s);
			found = true;
		}
		
		assertTrue("Couldnt find myField !!", found);
				
		// new MyTransformer(dbConfig, "some-test-id").saveCascade(myRel);				
		
		// assertEquals("HELLO", myRel.getmyField());
	}

	private static class MyTransformer extends UBYHibernateTransformer {

		String lexicalResourceId;
		
		public MyTransformer(DBConfig dbConfig, String lexicalResourceId) {
			super(dbConfig);
			this.lexicalResourceId = lexicalResourceId;
		}

		@Override
		protected String getResourceAlias() {
			return lexicalResourceId;
		}
		
		public void saveCascade(Object obj){
			super.saveCascade(obj);
		}
	}
	
}
