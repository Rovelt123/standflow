import test from 'node:test'
import assert from 'node:assert/strict'
import { applicationSearchScore } from '../src/pages/kontrolpanel/applicationSearch.js'

test('matches customer-note words without case sensitivity and handles Danish letters', () => {
  const application = { company: 'Keramik', customerNote: 'Oplys venligst dit CVR. Ændr adresse.' }
  assert.ok(applicationSearchScore(application, 'cVr') >= 0)
  assert.ok(applicationSearchScore(application, 'ændr adresse') >= 0)
  assert.equal(applicationSearchScore(application, 'ukendt'), -1)
})

//--------------------------------------------------------------

test('company matches outrank note matches and exact company matches rank first', () => {
  const exact = { company: 'Keramik', customerNote: '' }
  const company = { company: 'Peters Keramik', customerNote: '' }
  const note = { company: 'Andre produkter', customerNote: 'Vi mangler info om keramik' }
  assert.ok(applicationSearchScore(exact, 'keramik') > applicationSearchScore(company, 'keramik'))
  assert.ok(applicationSearchScore(company, 'keramik') > applicationSearchScore(note, 'keramik'))
})

//--------------------------------------------------------------

test('whole note words outrank partial words and all search words must match', () => {
  assert.ok(applicationSearchScore({ customerNote: 'CVR mangler' }, 'cvr')
    > applicationSearchScore({ customerNote: 'CVRnummer mangler' }, 'cvr'))
  assert.ok(applicationSearchScore({ company: 'Keramik', customerNote: 'CVR mangler' }, 'keramik cvr') >= 0)
  assert.equal(applicationSearchScore({ customerNote: 'CVR mangler' }, 'cvr strøm'), -1)
})

//--------------------------------------------------------------

test('preserves existing search fields and ignores private comments', () => {
  for (const field of ['company', 'contact', 'email', 'cvr', 'city']) {
    assert.ok(applicationSearchScore({ [field]: 'København' }, 'københavn') >= 0)
  }
  assert.equal(applicationSearchScore({ comment: 'Private' }, 'private'), -1)
  assert.equal(applicationSearchScore({}, ''), 0)
  assert.equal(applicationSearchScore({ customerNote: null }, 'cvr'), -1)
})

//--------------------------------------------------------------

test('ranks matching applications with closest company first, then company and note matches', () => {
  const applications = [
    { id: 'partial-note', company: 'Glas', customerNote: 'Keramikvarer mangler' },
    { id: 'note', company: 'Træ', customerNote: 'Oplys mere om keramik' },
    { id: 'company', company: 'Peters Keramik' },
    { id: 'unrelated', company: 'Smykker', customerNote: 'Mangler CVR' },
    { id: 'exact-company', company: 'Keramik' },
  ]
  const matches = applications
    .filter(application => applicationSearchScore(application, 'keramik') >= 0)
    .sort((a, b) => applicationSearchScore(b, 'keramik') - applicationSearchScore(a, 'keramik'))
  assert.deepEqual(matches.map(application => application.id),
    ['exact-company', 'company', 'note', 'partial-note'])
})
