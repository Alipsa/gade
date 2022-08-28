package examplepackage

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CountryTest {

  @Test
  void testCountryTable() {
    Assertions.assertEquals(Country.values().size(), Country.asTable().size());
  }
}
