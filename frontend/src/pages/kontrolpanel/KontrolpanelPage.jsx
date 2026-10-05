import AdminTopBar from '../../components/admin/AdminTopBar.jsx'
import AdminSidebar from '../../components/admin/AdminSidebar.jsx'
import StatTile from '../../components/admin/StatTile.jsx'
import ApplicationsToolbar from '../../components/admin/ApplicationsToolbar.jsx'
import ApplicationsTable from '../../components/admin/ApplicationsTable.jsx'
import { applications, stats } from './mockData.js'
import styles from './KontrolpanelPage.module.css'

function KontrolpanelPage() {
  return (
    <div className={styles.page}>
      <AdminTopBar title="Kontrolpanel" />
      <AdminSidebar />
      <main className={styles.main}>
        <div className={styles.stats}>
          {stats.map((stat) => (
            <StatTile key={stat.label} {...stat} />
          ))}
        </div>
        <ApplicationsToolbar />
        <ApplicationsTable applications={applications} />
      </main>
    </div>
  )
}

export default KontrolpanelPage
